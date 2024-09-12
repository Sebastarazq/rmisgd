import java.rmi.server.UnicastRemoteObject;
import java.rmi.RemoteException;
import java.sql.*;
import java.sql.Date;
import java.util.*;
import java.nio.file.*;
import java.io.*;

public class DocumentManagerImpl extends UnicastRemoteObject implements DocumentManagerInterface {

    private Connection conexion;
    private static final String DIRECTORIO_ARCHIVOS = System.getProperty("user.home") + File.separator + "SGD_Archivos";

    public DocumentManagerImpl() throws RemoteException {
        super();
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            conexion = DriverManager.getConnection("jdbc:mysql://localhost:3306/SGD", "root", "1234");
            
            // Crear el directorio si no existe
            Files.createDirectories(Paths.get(DIRECTORIO_ARCHIVOS));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean autenticarUsuario(String email, String password) throws RemoteException {
        try {
            String query = "SELECT * FROM usuarios WHERE email = ? AND password = ?";
            PreparedStatement stmt = conexion.prepareStatement(query);
            stmt.setString(1, email);
            stmt.setString(2, password);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public List<String> listarDocumentos(int usuarioId) throws RemoteException {
        List<String> documentos = new ArrayList<>();
        try {
            // Consulta simplificada para obtener todos los documentos sin verificar permisos
            String query = "SELECT DISTINCT d.nombre FROM documentos d " +
                        "JOIN usuarios u ON d.usuario_id = u.id " +
                        "WHERE u.id = ?";
            PreparedStatement stmt = conexion.prepareStatement(query);
            stmt.setInt(1, usuarioId);
            ResultSet rs = stmt.executeQuery();
            
            System.out.println("Ejecutando listarDocumentos con usuarioId: " + usuarioId);
            
            while (rs.next()) {
                String nombre = rs.getString("nombre");
                System.out.println("Documento encontrado: " + nombre);
                documentos.add(nombre);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return documentos;
    }


    @Override
    public byte[] descargarDocumento(String nombreArchivo, int usuarioId) throws RemoteException {
        if (nombreArchivo == null || nombreArchivo.isEmpty()) {
            System.err.println("Nombre de archivo es nulo o vacío");
            return null;
        }

        try {
            // Obtener el nombre del usuario
            String nombreUsuario;
            try {
                nombreUsuario = obtenerNombreUsuarioPorId(usuarioId);
            } catch (SQLException e) {
                System.err.println("Error al obtener el nombre del usuario: " + e.getMessage());
                e.printStackTrace();
                return null;
            }

            // Crear la ruta completa al archivo del usuario
            Path directorioUsuario = Paths.get(DIRECTORIO_ARCHIVOS, nombreUsuario);
            Path path = directorioUsuario.resolve(nombreArchivo);

            if (!Files.exists(path)) {
                System.err.println("El archivo no existe en la ruta: " + path.toString());
                return null;
            }

            return Files.readAllBytes(path);
        } catch (IOException e) {
            System.err.println("Error al leer el archivo: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public boolean subirDocumento(String nombreArchivo, byte[] contenido, int usuarioId) throws RemoteException {
        try {
            // Obtener el nombre del usuario
            String nombreUsuario = obtenerNombreUsuarioPorId(usuarioId); // Nueva función para obtener el nombre por ID
            
            // Crear el directorio del usuario si no existe
            Path directorioUsuario = Paths.get(DIRECTORIO_ARCHIVOS, nombreUsuario);
            if (!Files.exists(directorioUsuario)) {
                Files.createDirectories(directorioUsuario);
            }

            // Guardar el archivo dentro del directorio del usuario
            Path path = directorioUsuario.resolve(nombreArchivo);
            Files.write(path, contenido);

            // Insertar en la base de datos
            String query = "INSERT INTO documentos (nombre, ruta, usuario_id) VALUES (?, ?, ?)";
            PreparedStatement stmt = conexion.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
            stmt.setString(1, nombreArchivo);
            stmt.setString(2, path.toString());
            stmt.setInt(3, usuarioId);
            stmt.executeUpdate();

            // Obtener el ID del documento recién insertado
            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                int documentoId = rs.getInt(1);

                // Insertar permiso para el usuario que subió el documento
                query = "INSERT INTO permisos (usuario_id, documento_id, permiso) VALUES (?, ?, 'escritura')";
                stmt = conexion.prepareStatement(query);
                stmt.setInt(1, usuarioId);
                stmt.setInt(2, documentoId);
                stmt.executeUpdate();
            }

            return true;
        } catch (IOException | SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean renombrarDocumento(String nombreActual, String nuevoNombre, int usuarioId) throws RemoteException {
        try {
            // Obtener el nombre del usuario
            String nombreUsuario = obtenerNombreUsuarioPorId(usuarioId);

            // Verificar permisos
            if (!tienePermiso(usuarioId, nombreActual, "escritura")) {
                return false;
            }

            // Renombrar el archivo dentro del directorio del usuario
            Path directorioUsuario = Paths.get(DIRECTORIO_ARCHIVOS, nombreUsuario);
            Path oldPath = directorioUsuario.resolve(nombreActual);
            Path newPath = directorioUsuario.resolve(nuevoNombre);
            Files.move(oldPath, newPath);

            // Actualizar en la base de datos
            String query = "UPDATE documentos SET nombre = ?, ruta = ? WHERE nombre = ? AND usuario_id = ?";
            PreparedStatement stmt = conexion.prepareStatement(query);
            stmt.setString(1, nuevoNombre);
            stmt.setString(2, newPath.toString());
            stmt.setString(3, nombreActual);
            stmt.setInt(4, usuarioId);
            stmt.executeUpdate();

            return true;
        } catch (IOException | SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public String mostrarPropiedades(String nombreArchivo) throws RemoteException {
        File archivo = new File(DIRECTORIO_ARCHIVOS, nombreArchivo);
        if (archivo.exists()) {
            long tamaño = archivo.length();
            Date fechaModificacion = new Date(archivo.lastModified());
            return "Tamaño: " + tamaño + " bytes, Última modificación: " + fechaModificacion.toString();
        } else {
            return "El archivo no existe.";
        }
    }

    private boolean tienePermiso(int usuarioId, String nombreArchivo, String tipoPermiso) throws SQLException {
        String query = "SELECT p.permiso FROM permisos p " +
                       "JOIN documentos d ON p.documento_id = d.id " +
                       "WHERE p.usuario_id = ? AND d.nombre = ?";
        PreparedStatement stmt = conexion.prepareStatement(query);
        stmt.setInt(1, usuarioId);
        stmt.setString(2, nombreArchivo);
        ResultSet rs = stmt.executeQuery();
        if (rs.next()) {
            String permiso = rs.getString("permiso");
            return permiso.equals(tipoPermiso) || (tipoPermiso.equals("lectura") && permiso.equals("escritura"));
        }
        return false;
    }

    @Override
    public String obtenerNombreUsuario(String email) throws RemoteException {
        try {
            String query = "SELECT nombre FROM usuarios WHERE email = ?";
            PreparedStatement stmt = conexion.prepareStatement(query);
            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("nombre");
            }
            return "Usuario desconocido";
        } catch (SQLException e) {
            e.printStackTrace();
            return "Error al obtener el nombre";
        }
    }

    @Override
    public int obtenerUsuarioId(String email) throws RemoteException {
        try {
            String query = "SELECT id FROM usuarios WHERE email = ?";
            PreparedStatement stmt = conexion.prepareStatement(query);
            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1; // Retorna un valor de error si no se encuentra el usuario
    }

    
    // Metodos de la interfaz AdminInterface

    @Override
    public boolean autenticarAdmin(String email, String password) throws RemoteException {
        try {
            String query = "SELECT * FROM usuarios WHERE email = ? AND password = ? AND rol_id = (SELECT id FROM roles WHERE nombre = 'admin')";
            PreparedStatement stmt = conexion.prepareStatement(query);
            stmt.setString(1, email);
            stmt.setString(2, password);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean agregarUsuario(String nombre, String email, String password) throws RemoteException {
        try {
            // Asumiendo que el ID del rol de usuario normal es 2
            int rolId = 2;

            String query = "INSERT INTO usuarios (nombre, email, password, rol_id) VALUES (?, ?, ?, ?)";
            PreparedStatement stmt = conexion.prepareStatement(query);
            stmt.setString(1, nombre);
            stmt.setString(2, email);
            stmt.setString(3, password);
            stmt.setInt(4, rolId); // Establecer el rol del usuario a "normal"
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean eliminarUsuario(int usuarioId) throws RemoteException {
        try {
            // Eliminar permisos asociados
            String query = "DELETE FROM permisos WHERE usuario_id = ?";
            PreparedStatement stmt = conexion.prepareStatement(query);
            stmt.setInt(1, usuarioId);
            stmt.executeUpdate();

            // Eliminar documentos asociados
            query = "DELETE FROM documentos WHERE usuario_id = ?";
            stmt = conexion.prepareStatement(query);
            stmt.setInt(1, usuarioId);
            stmt.executeUpdate();

            // Eliminar el usuario
            query = "DELETE FROM usuarios WHERE id = ?";
            stmt = conexion.prepareStatement(query);
            stmt.setInt(1, usuarioId);
            stmt.executeUpdate();

            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean editarUsuario(int usuarioId, String nombre, String email, String password) throws RemoteException {
        try {
            String query = "UPDATE usuarios SET nombre = ?, email = ?, password = ? WHERE id = ?";
            PreparedStatement stmt = conexion.prepareStatement(query);
            stmt.setString(1, nombre);
            stmt.setString(2, email);
            stmt.setString(3, password);
            stmt.setInt(4, usuarioId);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public List<Usuario> listarUsuarios() throws RemoteException {
        List<Usuario> usuarios = new ArrayList<>();
        try {
            String query = "SELECT u.id, u.nombre, u.email, r.nombre AS rol FROM usuarios u " +
                           "JOIN roles r ON u.rol_id = r.id";
            PreparedStatement stmt = conexion.prepareStatement(query);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                int id = rs.getInt("id");
                String nombre = rs.getString("nombre");
                String email = rs.getString("email");
                String rol = rs.getString("rol");
                usuarios.add(new Usuario(id, nombre, email, rol));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return usuarios;
    }

    @Override
    public List<String> listarDocumentosUsuario(int usuarioId) throws RemoteException {
        List<String> documentos = new ArrayList<>();
        try {
            String query = "SELECT nombre FROM documentos WHERE usuario_id = ?";
            PreparedStatement stmt = conexion.prepareStatement(query);
            stmt.setInt(1, usuarioId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                documentos.add(rs.getString("nombre"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return documentos;
    }

    @Override
    public boolean eliminarDocumento(String nombreArchivo, int usuarioId) throws RemoteException {
        try {
            // Obtener el nombre del usuario
            String nombreUsuario = obtenerNombreUsuarioPorId(usuarioId);

            // Eliminar el documento de la base de datos
            String query = "DELETE FROM documentos WHERE nombre = ? AND usuario_id = ?";
            PreparedStatement stmt = conexion.prepareStatement(query);
            stmt.setString(1, nombreArchivo);
            stmt.setInt(2, usuarioId);
            int rowsAffected = stmt.executeUpdate();

            // Eliminar el archivo del sistema de archivos
            Path directorioUsuario = Paths.get(DIRECTORIO_ARCHIVOS, nombreUsuario);
            Path path = directorioUsuario.resolve(nombreArchivo);
            Files.deleteIfExists(path);

            return rowsAffected > 0;
        } catch (IOException | SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Nueva función para obtener el nombre del usuario por su ID
    private String obtenerNombreUsuarioPorId(int usuarioId) throws SQLException {
        String query = "SELECT nombre FROM usuarios WHERE id = ?";
        PreparedStatement stmt = conexion.prepareStatement(query);
        stmt.setInt(1, usuarioId);
        ResultSet rs = stmt.executeQuery();
        if (rs.next()) {
            return rs.getString("nombre");
        }
        return "UsuarioDesconocido"; // Retornar un valor por defecto si no se encuentra
    }

    @Override
    public boolean compartirDocumento(int documentoId, int usuarioId, String permiso) throws RemoteException {
        try {
            // Insertar el permiso en la base de datos
            String query = "INSERT INTO permisos (usuario_id, documento_id, permiso) VALUES (?, ?, ?)";
            PreparedStatement stmt = conexion.prepareStatement(query);
            stmt.setInt(1, usuarioId);
            stmt.setInt(2, documentoId);
            stmt.setString(3, permiso);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }




    @Override
    public Usuario buscarUsuarioPorEmail(String email) throws RemoteException {
        try {
            String query = "SELECT id, nombre, email, rol_id FROM usuarios WHERE email = ?";
            PreparedStatement stmt = conexion.prepareStatement(query);
            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                int id = rs.getInt("id");
                String nombre = rs.getString("nombre");
                int rolId = rs.getInt("rol_id");

                // Obtener el nombre del rol
                String rolQuery = "SELECT nombre FROM roles WHERE id = ?";
                PreparedStatement rolStmt = conexion.prepareStatement(rolQuery);
                rolStmt.setInt(1, rolId);
                ResultSet rolRs = rolStmt.executeQuery();
                String rol = "Desconocido";
                if (rolRs.next()) {
                    rol = rolRs.getString("nombre");
                }

                // Crear y devolver el objeto Usuario
                return new Usuario(id, nombre, email, rol);
            } else {
                return null; // Usuario no encontrado
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RemoteException("Error al buscar el usuario por email", e);
        }
    }

    @Override
    public int obtenerDocumentoId(String nombreArchivo) throws RemoteException {
        try {
            String query = "SELECT id FROM documentos WHERE nombre = ?";
            PreparedStatement stmt = conexion.prepareStatement(query);
            stmt.setString(1, nombreArchivo);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1; // Retorna -1 si no se encuentra el documento
    }
}