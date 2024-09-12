import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface DocumentManagerInterface extends Remote {
    // Métodos de AdminInterface
    boolean autenticarAdmin(String email, String password) throws RemoteException;
    boolean agregarUsuario(String nombre, String email, String password) throws RemoteException;
    boolean eliminarUsuario(int usuarioId) throws RemoteException;
    boolean editarUsuario(int usuarioId, String nombre, String email, String password) throws RemoteException;
    List<Usuario> listarUsuarios() throws RemoteException;
    List<String> listarDocumentosUsuario(int usuarioId) throws RemoteException;
    boolean eliminarDocumento(String nombreArchivo, int usuarioId) throws RemoteException;
    
    // Métodos específicos de DocumentManagerInterface
    boolean autenticarUsuario(String email, String password) throws RemoteException;
    List<String> listarDocumentos(int usuarioId) throws RemoteException;
    int obtenerUsuarioId(String email) throws RemoteException;
    byte[] descargarDocumento(String nombreArchivo, int usuarioId) throws RemoteException;
    boolean subirDocumento(String nombreArchivo, byte[] contenido, int usuarioId) throws RemoteException;
    boolean renombrarDocumento(String nombreActual, String nuevoNombre, int usuarioId) throws RemoteException;
    String mostrarPropiedades(String nombreArchivo) throws RemoteException;
    String obtenerNombreUsuario(String email) throws RemoteException;
    Usuario buscarUsuarioPorEmail(String email) throws RemoteException;

    // Compartir documento
    boolean compartirDocumento(int documentoId, int usuarioId, String permiso) throws RemoteException;
    int obtenerDocumentoId(String nombreArchivo) throws RemoteException;

}
