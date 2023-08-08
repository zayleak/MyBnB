import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Scanner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public class Report {

    public static void numBookings(Connection conn, Scanner sc) {
        System.out.println("[ REPORT BOOKINGS WITHIN RANGE ]");

        String startStr;
        do {
            System.out.print("Start date (yyyy-mm-dd): ");
            startStr = sc.nextLine().trim();
        } while (!startStr.matches("\\d{4}-\\d{2}-\\d{2}"));
        String endStr;
        do {
            System.out.print("End date (yyyy-mm-dd): ");
            endStr = sc.nextLine().trim();
        } while (!endStr.matches("\\d{4}-\\d{2}-\\d{2}"));

        try {
            LocalDate start = LocalDate.parse(startStr);
            LocalDate end = LocalDate.parse(endStr);

            try {
                String city = "SELECT city, COUNT(bid) AS num FROM Listing JOIN Booking USING (lid) "
                            + "WHERE finished = 0 AND ? <= end_date AND ? >= start_date GROUP BY city";
                String postal = "SELECT postal, COUNT(bid) AS num FROM Listing JOIN Booking USING (lid) "
                            + "WHERE finished = 0 AND ? <= end_date AND ? >= start_date GROUP BY postal";

                PreparedStatement stmt = conn.prepareStatement(city); 
                stmt.setObject(1, start);
                stmt.setObject(2, end);
                ResultSet rs = stmt.executeQuery();
                System.out.println("[ BY CITY ]");
                while (rs.next()) {
                    System.out.print("City: " + rs.getString("city") + " | ");
                    System.out.println("Bookings: " + rs.getString("num"));
                }
                rs.close();
                stmt.close();

                stmt = conn.prepareStatement(postal);
                stmt.setObject(1, start);
                stmt.setObject(2, end);
                rs = stmt.executeQuery();
                System.out.println("[ BY POSTAL CODE ]");
                while (rs.next()) {
                    System.out.print("Postal: " + rs.getString("postal") + " | ");
                    System.out.println("Bookings: " + rs.getString("num"));
                }
                rs.close();
                stmt.close();
            } catch (SQLException e) {
                System.err.println("Failed report...");
                e.printStackTrace();
                return;
            }
        } catch (DateTimeParseException e) {
            System.err.println("Invalid date input...");
            return;
        }
    }

    public static void numListings(Connection conn) {

        String country = "SELECT COUNT(lid) as num, country FROM Listing GROUP BY country";
        String countryCity = "SELECT COUNT(lid) as num, country, city FROM Listing GROUP BY country, city";
        String countryCityPostal = "SELECT COUNT(lid) as num, country, city, postal FROM Listing GROUP BY country, city, postal";
        
        try {
            Statement stmt = conn.createStatement();

            ResultSet rs = stmt.executeQuery(country);
            System.out.println("[ BY COUNTRY ]");
            while (rs.next()) {
                System.out.print(rs.getString("country") + ": ");
                System.out.println(rs.getInt("num"));
            }

            rs = stmt.executeQuery(countryCity);            
            System.out.println("[ BY COUNTRY, CITY ]");
            while (rs.next()) {
                System.out.print(rs.getString("country") + ", ");
                System.out.print(rs.getString("city") + ": ");
                System.out.println(rs.getInt("num"));
            }

            rs = stmt.executeQuery(countryCityPostal);
            System.out.println("[ BY COUNTRY, CITY, POSTAL CODE ]");
            while (rs.next()) {
                System.out.print(rs.getString("country") + ", ");
                System.out.print(rs.getString("city") + ", ");
                System.out.print(rs.getString("postal") + ": ");
                System.out.println(rs.getInt("num"));
            }
            stmt.close();

        } catch (SQLException e) {
            System.err.println("Failed report...");
            e.printStackTrace();
        }
    }

    public static void hostListingRanking(Connection conn) {
        String country = "SELECT COUNT(lid) as num, name, country "
                            + "FROM Listing JOIN User USING (uid) "
                            + "GROUP BY country, uid ORDER BY country, COUNT(lid) DESC";
        String city = "SELECT COUNT(lid) as num, name, city "
                            + "FROM Listing JOIN User USING (uid) "
                            + "GROUP BY city, uid ORDER BY city, COUNT(lid) DESC";
        String prev;
        String curr;

        try {
            Statement stmt = conn.createStatement();

            prev = "";
            ResultSet rs = stmt.executeQuery(country);
            System.out.println("[ RANKING BY COUNTRY ]");
            while (rs.next()) {
                curr = rs.getString("country");
                if (!prev.equalsIgnoreCase(curr))
                    System.out.println("x-- " + curr);
                prev = curr;
                System.out.println("|\t" + rs.getString("name") + ", " + rs.getInt("num"));
            }
            System.out.println("x");
            prev = "";
            rs = stmt.executeQuery(city);
            System.out.println("[ RANKING BY CITY ]");
            while (rs.next()) {
                curr = rs.getString("city");
                if (!prev.equalsIgnoreCase(curr))
                    System.out.println("x-- " + curr);
                prev = curr;
                System.out.println("|\t" + rs.getString("name") + ": " + rs.getInt("num"));
            }
            System.out.println("x");
            stmt.close();
        } catch (SQLException e) {
            System.err.println("Failed report...");
            e.printStackTrace();
            return;
        }
    }

    public static void renterBookingRanking(Connection conn, Scanner sc) {
        String startStr;
        do {
            System.out.print("Start date (yyyy-mm-dd): ");
            startStr = sc.nextLine().trim();
        } while (!startStr.matches("\\d{4}-\\d{2}-\\d{2}"));
        String endStr;
        do {
            System.out.print("End date (yyyy-mm-dd): ");
            endStr = sc.nextLine().trim();
        } while (!endStr.matches("\\d{4}-\\d{2}-\\d{2}"));

        try {
            LocalDate start = LocalDate.parse(startStr);
            LocalDate end = LocalDate.parse(endStr);

            try {
                String topRenters = "SELECT COUNT(bid) AS num, name FROM User LEFT JOIN Booking USING (uid) " 
                                + "WHERE finished = 0 AND ? <= end_date AND ? >= start_date "
                                + "GROUP BY uid ORDER BY COUNT(bid) DESC";
                String rankingPerCity = 
                        "SELECT COUNT(B.bid) AS num, U.name, L.city "
                        + "FROM User U LEFT JOIN Booking B USING (uid) JOIN Listing L USING (lid) "
                        + "WHERE EXISTS (SELECT COUNT(*) FROM Booking B2 WHERE B2.uid = U.uid "
                        +           "AND B2.start_date <= CURDATE() AND B2.end_date >= DATE_SUB(CURDATE(), INTERVAL 1 YEAR) "
                        +           "GROUP BY B2.uid HAVING COUNT(*) > 1) "
                        +     "AND finished = 0 AND ? <= end_date AND ? >= start_date "
                        + "GROUP BY L.city, U.name, B.uid "
                        + "ORDER BY L.city, num DESC";
                PreparedStatement stmt = conn.prepareStatement(topRenters); 
                stmt.setObject(1, start);
                stmt.setObject(2, end);
                ResultSet rs = stmt.executeQuery();
                System.out.println("[ BY TOP RENTERS ]");
                while (rs.next()) {
                    System.out.print("Name: " + rs.getString("name") + " | ");
                    System.out.println("Bookings: " + rs.getString("num"));
                }
                rs.close();
                stmt.close();

                stmt = conn.prepareStatement(rankingPerCity);
                stmt.setObject(1, start);
                stmt.setObject(2, end);
                rs = stmt.executeQuery();
                String prev = "";
                System.out.println("[ RANKING BY CITY ]");
                while (rs.next()) {
                    String curr = rs.getString("city");
                    if (!prev.equalsIgnoreCase(curr))
                        System.out.println("x-- " + curr);
                    prev = curr;
                    System.out.println("|\t" + rs.getString("name") + ": " + rs.getInt("num"));
                }
                System.out.println("x");
                stmt.close();
            } catch (SQLException e) {
                System.err.println("Failed report...");
                e.printStackTrace();
                return;
            }
        } catch (DateTimeParseException e) {
            System.err.println("Invalid date input...");
            e.printStackTrace();
            return;
        }
    }

    public static void possibleCommercialHosts(Connection conn) {
        System.out.println("[ HOSTS w/ >10% TOTAL LISTINGS ]");

        String topCountry = "SELECT COUNT(lid) as num, country, name "
                + "FROM Listing L1 JOIN User U1 USING (uid) "
                + "GROUP BY country, uid HAVING COUNT(lid) > 0.1 * "
                + "(SELECT COUNT(L2.lid) FROM Listing L2 "
                + "WHERE L1.country = L2.country GROUP BY L2.country) "
                + "ORDER BY country";
                        
        String topCity = "SELECT COUNT(lid) as num, city, name "
                + "FROM Listing L1 JOIN User U1 USING (uid) "
                + "GROUP BY city, uid HAVING COUNT(lid) > 0.1 * "
                + "(SELECT COUNT(L2.lid) FROM Listing L2 "
                + "WHERE L1.city = L2.city GROUP BY L2.city) "
                + "ORDER BY city";

        String prev;
        String curr;

        try {    
            Statement stmt = conn.createStatement();

            prev = "";
            ResultSet rs = stmt.executeQuery(topCountry);
            System.out.println("[ BY COUNTRY ]");
            while (rs.next()) {
                curr = rs.getString("country");
                if (!prev.equalsIgnoreCase(curr))
                    System.out.println(curr);
                prev = curr;
                System.out.println("\t" + rs.getString("name") + ": " + rs.getInt("num"));
            }

            prev = "";
            rs = stmt.executeQuery(topCity);
            System.out.println("[ BY CITY ]");
            while (rs.next()) {
                curr = rs.getString("city");
                if (!prev.equalsIgnoreCase(curr))
                    System.out.println(curr);
                prev = curr;
                System.out.println("\t" + rs.getString("name") + ": " + rs.getInt("num"));
            }
            stmt.close();


            stmt.close();

        } catch (SQLException e) {
            System.err.println("Failed report...");
            e.printStackTrace();
            return;
        }
    }

    public static void mostCancellations(Connection conn) {
    	String hosts = "SELECT COUNT(*) AS num, name "
                + "FROM Cancellation "
                + "INNER JOIN Booking ON Cancellation.bid = Booking.bid "
                + "JOIN User ON Booking.host = User.uid "
                + "WHERE finished = 1 AND Cancellation.cancel_date BETWEEN DATE_SUB(CURDATE(), INTERVAL 1 YEAR) AND CURDATE() "
                + "GROUP BY Booking.host HAVING COUNT(*) >= ALL ("
                    + "SELECT COUNT(*) "
                    + "FROM Cancellation "
                    + "INNER JOIN Booking ON Cancellation.bid = Booking.bid "
                    + "JOIN User ON Booking.host = User.uid "
                    + "WHERE finished = 1 AND Cancellation.cancel_date BETWEEN DATE_SUB(CURDATE(), INTERVAL 1 YEAR) AND CURDATE() "
                    + "GROUP BY Booking.host)";
        String renters = "SELECT COUNT(*) AS num, name "
                + "FROM Cancellation "
                + "INNER JOIN Booking ON Cancellation.bid = Booking.bid "
                + "JOIN User ON Booking.uid = User.uid "
                + "WHERE finished = 2 AND Cancellation.cancel_date BETWEEN DATE_SUB(CURDATE(), INTERVAL 1 YEAR) AND CURDATE() "
                + "GROUP BY Booking.uid HAVING COUNT(*) >= ALL ("
                    + "SELECT COUNT(*) "
                    + "FROM Cancellation "
                    + "INNER JOIN Booking ON Cancellation.bid = Booking.bid "
                    + "JOIN User ON Booking.uid = User.uid "
                    + "WHERE finished = 2 AND Cancellation.cancel_date BETWEEN DATE_SUB(CURDATE(), INTERVAL 1 YEAR) AND CURDATE() "
                    + "GROUP BY Booking.uid)";

        try {    
            Statement stmt = conn.createStatement();

            ResultSet rs = stmt.executeQuery(hosts);
            System.out.println("[ HOST ]");
            if (rs.next() == false) {
                System.out.println("No host cancellations within a year.");
            } else {
                do {
                    System.out.println(rs.getString("name") + ", " + rs.getInt("num"));
                } while (rs.next());
            }

            rs = stmt.executeQuery(renters);
            System.out.println("[ RENTER ]");
            if (rs.next() == false) {
                System.out.println("No renter cancellations within a year.");
            } else {
                do {
                    System.out.println(rs.getString("name") + ", " + rs.getInt("num"));
                } while (rs.next());
            }
            stmt.close();

        } catch (SQLException e) {
            System.err.println("Failed report...");
            e.printStackTrace();
            return;
        }
    }

    public static void listingPopularWords(Connection conn) {
    	try {
    	    // Query to retrieve cleaned comments and listing information
    	    String query = "SELECT lid, "
                        + "REGEXP_REPLACE(Comment.body, '[[:punct:]]', ' ') AS cleaned_comment "
                        + "FROM ListingComment INNER JOIN Comment USING (cid)";

            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(query);

    	    Map<Integer, Map<String, Integer>> listingWordFrequencies = new HashMap<>();

    	    while (rs.next()) {
    	        int listingId = rs.getInt(1);
    	        String cleanedComment = rs.getString(2);

    	        // Tokenize cleaned comment text and update word frequencies
    	        String[] words = cleanedComment.toLowerCase().split("\\s+");
    	        Map<String, Integer> wordFrequency = listingWordFrequencies.computeIfAbsent(listingId, k -> new HashMap<>());

    	        for (String word : words) {
    	            if (word.length() >= 5) { // Include words with length >= 5
    	                wordFrequency.put(word, wordFrequency.getOrDefault(word, 0) + 1);
    	            }
    	        }
    	    }

    	    // Print the three most popular words and their frequencies per listing
    	    for (Map.Entry<Integer, Map<String, Integer>> entry : listingWordFrequencies.entrySet()) {
    	        int listingId = entry.getKey();
    	        Map<String, Integer> wordFrequency = entry.getValue();

    	        System.out.println("Listing ID: " + listingId);

    	        // Sort words by frequency in descending order
    	        List<Entry<String, Integer>> sortedWordFrequency = wordFrequency.entrySet()
    	                .stream()
    	                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
    	                .limit(3) // Get the top 3 words
    	                .collect(Collectors.toList());

    	        for (Map.Entry<String, Integer> wordEntry : sortedWordFrequency) {
    	            System.out.println("\t" + wordEntry.getKey() + ": " + wordEntry.getValue());
    	        }
    	        System.out.println();
    	    }

    	} catch (SQLException e) {
    	    e.printStackTrace();
    	}
    }

}







