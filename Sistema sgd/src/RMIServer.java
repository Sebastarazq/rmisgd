import java.rmi.Naming;
import java.rmi.registry.LocateRegistry;

public class RMIServer {
    public static void main(String[] args) {
        try {
            LocateRegistry.createRegistry(1099);  // Levantar el registro en el puerto 1099
            DocumentManagerImpl obj = new DocumentManagerImpl();
            Naming.rebind("//localhost/DocumentManager", obj);
            System.out.println("Servidor RMI listo.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
