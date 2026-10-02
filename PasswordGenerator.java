import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordGenerator {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        System.out.println("SPERS-001 : " + encoder.encode("Demo1234!"));
        System.out.println("SPERS-002 : " + encoder.encode("Demo1234!"));
        System.out.println("SPERS-003 : " + encoder.encode("Demo1234!"));
        System.out.println("SPERS-004 : " + encoder.encode("Demo1234!"));
        System.out.println("SPERS-005 : " + encoder.encode("Demo1234!"));
        System.out.println("SPERS-006 : " + encoder.encode("Demo1234!"));
    }
}
