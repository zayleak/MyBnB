import java.sql.Statement;
import java.sql.Connection;
import java.sql.SQLException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class Util {

    public static void loadStructure(Connection conn, String filePath) {
        try {
            Statement stmt = conn.createStatement();
            BufferedReader reader = new BufferedReader(new FileReader(filePath));

            String line;
            while ((line = reader.readLine()) != null)
                stmt.execute(line);

            stmt.close();
            reader.close();
		} catch (SQLException | IOException e) {
            e.printStackTrace();
		}
    }

    public static void loadData(Connection conn, String filePath) {
        try {
            Statement stmt = conn.createStatement();
            BufferedReader reader = new BufferedReader(new FileReader(filePath));

            String line;
            while ((line = reader.readLine()) != null)
                stmt.executeUpdate(line);

            stmt.close();
            reader.close();
        } catch (SQLException | IOException e) {
            e.printStackTrace();
        }
    }
}
