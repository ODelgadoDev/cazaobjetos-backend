package mx.utch.cazaobjetos.util;

import org.junit.jupiter.api.Test;
import mx.utch.cazaobjetos.enums.UserRole;
import static org.junit.jupiter.api.Assertions.*;

class StudentNumberUtilTest {
    @Test void recognizesOnlyTheStudentFormatAndKeepsLeadingZeroes() {
        assertEquals("0123456789", StudentNumberUtil.fromEmail("a0123456789@utch.edu.mx", UserRole.STUDENT));
        for (String email : new String[]{"alumno.demo@utch.edu.mx", "a123@utch.edu.mx", "1234567890@utch.edu.mx", "docente123@utch.edu.mx", "a0123456789@gmail.com", "a0123456789@utch.edu.mx.evil.com"})
            assertNull(StudentNumberUtil.fromEmail(email, UserRole.STUDENT), email);
        assertNull(StudentNumberUtil.fromEmail(null, UserRole.STUDENT));
        assertNull(StudentNumberUtil.fromEmail("a0123456789@utch.edu.mx", UserRole.TEACHER));
        assertNull(StudentNumberUtil.fromEmail("a0123456789@utch.edu.mx", UserRole.SECRETARY));
    }
}
