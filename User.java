import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Scanner;

public class User {
	int uid;
	String username;
	
	public User(int uid, String username) {
		this.uid = uid;
		this.username = username;
	}

    public static User newUser(Connection conn, Scanner sc) {
        System.out.print("Enter date of birth (yyyy-mm-dd): ");
        String dob = sc.nextLine();
        
        try {
            LocalDate date = LocalDate.parse(dob);
            if (Period.between(date, LocalDate.now()).getYears() < 18) {
                System.err.println("Must be at least 18 years old.");
                return null;
            }
        } catch (DateTimeParseException e) {
            System.err.println("Failed to parse input date.");
            return null;
        }

        String sin = "";
        do {
            System.out.print("Enter SIN number: ");
            sin = sc.nextLine();
            sin = sin.replaceAll("[-\\s]", "");
        } while (!sin.matches("\\d{9}"));

        System.out.print("Enter full name: ");
        String name = sc.nextLine();
        System.out.print("Enter address: ");
        String address = sc.nextLine();
        System.out.print("Enter occupation: ");
        String occupation = sc.nextLine();

        String username = "";
        do {
            System.out.print("Enter username: ");
            username = sc.nextLine();
        } while (!isUniqueInUser(conn, username, "username"));

        String password = "";
        do {
            System.out.print("Enter password: ");
            password = sc.nextLine();
        } while (!(password.length() > 0));

		String creditCard = getValidCard(sc);

	    // SQL statement to insert the new user into the User table
	    String insertQuery = "INSERT INTO User (password, sin, name, dob, occupation, address, username, card_number) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
	    
        try {                
            PreparedStatement stmt = conn.prepareStatement(insertQuery);
            stmt.setString(1, password);
            stmt.setString(2, sin);
            stmt.setString(3, name);
            stmt.setString(4, dob);
            stmt.setString(5, occupation);
            stmt.setString(6, address);
            stmt.setString(7, username);
            stmt.setString(8, creditCard);
			stmt.executeUpdate();
			stmt.close();

			Statement getID = conn.createStatement();
			ResultSet rs = getID.executeQuery("SELECT LAST_INSERT_ID() FROM User");
			rs.next();
			int uid = rs.getInt(1);
			return new User(uid, username);

		} catch (SQLException e) {
            e.printStackTrace();
            return null;
		}
    } 

    public static User login(Connection conn, Scanner sc) {
        System.out.println("========LOG IN========");
        System.out.print("Enter username: ");
        String username = sc.nextLine();
        System.out.print("Enter password: ");
        String password = sc.nextLine();

        try {
            Statement stmt = conn.createStatement();
            String sql = "SELECT * FROM User WHERE username = '" + username + 
                            "' AND password = '" + password + "'";
            ResultSet rs = stmt.executeQuery(sql);
            if (rs.next()) {
				return new User(rs.getInt("uid"), username);
            } else {
                System.err.println("No account with matching information.");
                return null;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }


    public static boolean delete(Connection conn, Scanner sc, int uid) {
        System.out.println("====DELETE ACCOUNT====");
        System.out.print("Confirm account deletion? (y/n): ");
        String delChoice = sc.nextLine();
        if (delChoice.compareToIgnoreCase("y") == 0){
            try {
                Statement stmt = conn.createStatement();
                String sql = "DELETE FROM User WHERE uid = '" + uid + "'";
                stmt.executeUpdate(sql);
                stmt.close();
            } catch (SQLException e) {
                System.err.println("Account deletion failed.");
                e.printStackTrace();
                return false;
            }
			System.out.println("Account deleted successfully.");
            return true;
        } else if (delChoice.compareToIgnoreCase("n") == 0) {
            return false;
        } else {
            System.err.println("Invalid input...");
        }
        return false;
    }

    private static boolean isUniqueInUser(Connection conn, String value, String column) {
        String sql = "SELECT COUNT(*) FROM User WHERE " + column + " = ?";
        try {
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, value);
            ResultSet usernames = stmt.executeQuery();
            usernames.next();
            int count = usernames.getInt(1);
            stmt.close();
            return count == 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private static String getValidCard(Scanner sc) {        
        String creditCardNumber;
        do {
            System.out.print("Enter a valid credit card number: ");
            creditCardNumber = sc.nextLine();
        } while (!isValidCard(creditCardNumber));

        return creditCardNumber;
    }

    private static boolean isValidCard(String cardNumber) {
        cardNumber = cardNumber.replaceAll("\\D", ""); // Remove non-digit characters

        if (cardNumber.length() < 13 || cardNumber.length() > 16)
            return false;
        return true;
    }

    public static String getCard(Connection conn, int uid) {
        String sql = "SELECT card_number FROM User WHERE uid = ?";
        try {
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, uid);
            ResultSet rs = stmt.executeQuery();
            rs.next();
            String card_number = rs.getString(1);
            stmt.close();
            return card_number;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

}


