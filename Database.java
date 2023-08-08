import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

@FunctionalInterface
interface PrepFunction {
  void apply(PreparedStatement prep) throws SQLException;
}

@FunctionalInterface
interface ValueFunction {
  Object apply(ResultSet resultSet) throws SQLException;
}

public class Database {
	
    @SuppressWarnings("unchecked")
	public static List<String[]> getUniqueRentersForHost(Connection conn, int hostId) {
	    String query = "SELECT DISTINCT CAST(U.uid AS CHAR) AS renter, U.username AS renter_username " +
	                   "FROM Booking B " +
	                   "INNER JOIN User U ON B.uid = U.uid " +
	                   "WHERE B.host = ? AND B.finished = 0 AND " +
                       "B.end_date BETWEEN DATE_SUB(CURDATE(), INTERVAL 1 MONTH) AND CURDATE()";

	    PrepFunction setFunc = (PreparedStatement prep) -> {
	        prep.setInt(1, hostId);
	    };

	    ValueFunction valueFunc = (ResultSet resultSet) -> {
	        List<String[]> uniqueRenterUserIdsAndUsernames = new ArrayList<>();
	        while (resultSet.next()) {
	            String renterId = resultSet.getString("renter");
	            String renterUsername = resultSet.getString("renter_username");
	            uniqueRenterUserIdsAndUsernames.add(new String[] { renterId, renterUsername });
	        }
	        return uniqueRenterUserIdsAndUsernames;
	    };

	    return (List<String[]>) getDBValues(conn, setFunc, query, valueFunc, new ArrayList<>());
	}
	
	
	public static void addCommentToRenter(Connection conn, Scanner sc, int uid) {
	    List<String[]> uniqueRenterUserIdsAndUsernames = getUniqueRentersForHost(conn, uid);

	    if (uniqueRenterUserIdsAndUsernames.isEmpty()) {
	        System.out.println("No renters found.");
	        return;
	    }

	    System.out.println("Select a renter to add a comment to:");
	    int count = 1;
	    for (String[] userInfo : uniqueRenterUserIdsAndUsernames) {
	        System.out.println(count + ". " + userInfo[1]);
	        count++;
	    }

	    System.out.print("Enter the number of the renter to add a comment to: ");
	    int selectedRenter = CommandLine.getIntInput(sc);

	    if (selectedRenter >= 1 && selectedRenter <= uniqueRenterUserIdsAndUsernames.size()) {
	        String renterId = uniqueRenterUserIdsAndUsernames.get(selectedRenter - 1)[0];
	        int renterUserId = Integer.parseInt(renterId);
	        
	        insertTypeComment(conn, sc, "RenterComment", "renter", uid, renterUserId);
	    } else {
	        System.out.println("Invalid selection.");
	    }
	}
	
	@SuppressWarnings("unchecked")
	public static List<String[]> getFinishedBookingsForListings(Connection conn, int uid) {
	    String query = "SELECT B.host, B.bid, L.type, L.address, L.city, L.country, U.username AS host_username, L.lid " +
	                   "FROM Booking B " +
	                   "INNER JOIN Listing L ON B.lid = L.lid " +
	                   "LEFT JOIN User U ON B.host = U.uid " +
	                   "WHERE B.uid = ? AND B.finished = 0 AND " + 
                       "B.end_date BETWEEN DATE_SUB(CURDATE(), INTERVAL 1 MONTH) AND CURDATE()";

	    PrepFunction setFunc = (PreparedStatement prep) -> {
	        prep.setInt(1, uid);
	    };

	    ValueFunction valueFunc = (ResultSet resultSet) -> {
	        List<String[]> finishedBookings = new ArrayList<>();
	        while (resultSet.next()) {
		        if (resultSet.getObject("host") != null ) {
		            String[] bookingInfo = {
                        Integer.toString(resultSet.getInt("bid")),
                        resultSet.getString("type"),
                        resultSet.getString("address"),
                        resultSet.getString("city"),
                        resultSet.getString("country"),
                        Integer.toString(resultSet.getInt("lid")),
                        resultSet.getString("host_username"),
                        Integer.toString(resultSet.getInt("host"))
			        };
		            finishedBookings.add(bookingInfo);
		        }
	        }
	        return finishedBookings;
	    };
	    return (List<String[]>) getDBValues(conn, setFunc, query, valueFunc, new ArrayList<>());
	}
	
	public static List<String> getUniqueHostUsernames(List<String[]> finishedBookings) {
	    Set<String> uniqueUsernames = new HashSet<>();
	    for (String[] bookingInfo : finishedBookings) {
	        uniqueUsernames.add(bookingInfo[6]);
	    }
	    return new ArrayList<>(uniqueUsernames);
	}
	
	public static void displayAndSelectBooking(Connection conn, Scanner sc, int uid, String targetType) {
	    List<String[]> finishedBookings = getFinishedBookingsForListings(conn, uid);

	    if (finishedBookings.isEmpty()) {
	        System.out.println("No entry of this type found.");
	        return;
	    }
	    List<String> uniqueHostUsernames = getUniqueHostUsernames(finishedBookings);
	    System.out.println("Select one of these to add a type comment for " + targetType);
	    int count = 1;
	    if (targetType.equals("host")) {
	        for (String username : uniqueHostUsernames) {
	            System.out.println(count + ". Host Username: " + username);
	            count++;
	        }
	    } else {
	        for (String[] bookingInfo : finishedBookings) {
	            System.out.println(count +
	                    ". Listing Type: " + bookingInfo[1] +
	                    ", Address: " + bookingInfo[2] +
	                    ", City: " + bookingInfo[3] +
	                    ", Country: " + bookingInfo[4]);
	            count++;
	        }
	    }

	    System.out.print("Enter the number of the booking to add a comment to the " + targetType + ": ");
	    int selectedBooking = CommandLine.getIntInput(sc);

	    if (selectedBooking >= 1 && selectedBooking <= (targetType.equals("host") ? uniqueHostUsernames.size() : finishedBookings.size())) {
	    	int targetId = targetType.equals("host") ? getHostIdByUsername(uniqueHostUsernames.get(selectedBooking - 1), finishedBookings) : Integer.parseInt(finishedBookings.get(selectedBooking - 1)[5]);
	        String type = targetType.equals("host") ? "HostComment" : "ListingComment";
	        String typeInsert = targetType.equals("host") ? "host" : "lid";
	        System.out.println("target id: " + targetId + " user id: " + uid);
	        insertTypeComment(conn, sc, type, typeInsert, uid, targetId);
	    } else {
	        System.out.println("Invalid selection.");
	    }
	}
	        
	
	public static int getHostIdByUsername(String username, List<String[]> finishedBookings) {
	    for (String[] bookingInfo : finishedBookings)
	        if (bookingInfo[6].equals(username))
	            return Integer.parseInt(bookingInfo[7]);
	    return 0; // Default value if the username is not found
	}
	
    public static Object getDBValues(Connection conn, PrepFunction setFunc, String setQuery, ValueFunction valueFunc, Object noFind) {
        try (PreparedStatement ps = conn.prepareStatement(setQuery, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE)) {
            setFunc.apply(ps);
            ResultSet resultSet = ps.executeQuery();
            if (resultSet.next() && !resultSet.previous())
            	return valueFunc.apply(resultSet);

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return noFind;

    }
    
    public static int insertComment(Connection conn, String body, int rating, int commenterId) {
    	return setDBValues(conn, ps -> {
            ps.setString(1, body);
            ps.setInt(2, rating);
            ps.setInt(3, commenterId);
        }, "INSERT INTO Comment (body, rating, commenter) VALUES (?, ?, ?)",
        "", "");
    }

    public static void insertTypeComment(Connection conn, Scanner sc, String type, String typeInsert, int commenterId, int typeId) {
        int rating;
        while (true) {
            System.out.print("Enter your rating (1 to 5): ");
            rating = CommandLine.getIntInput(sc);

            if (rating >= 1 && rating <= 5)
                break; // Valid rating entered, exit the loop
            System.out.println("Invalid rating. Please enter a number between 1 and 5.");
        }

        System.out.print("Enter your comment: ");
        String commentText = sc.nextLine();
    	
    	int commentId = insertComment(conn, commentText, rating, commenterId);
    	
    	setDBValues(conn, ps -> {
            ps.setInt(1, commentId);
            ps.setInt(2, typeId);
        }, "INSERT INTO " + type + " (cid, " + typeInsert + ") VALUES (?, ?)",
        "Sucessfully added comment", "Failed to add comment");
    }
	
    @SuppressWarnings("unchecked")
	public static List<String[]> listAvailableDates(Connection conn, int listingId, boolean wantBookings) {
        String firstQuery = "SELECT start_date, end_date, day_price, availability_id FROM Availability WHERE lid = ?";
        String query = wantBookings ? firstQuery + " UNION SELECT start_date, end_date, NULL as day_price, NULL as availability_id FROM Booking WHERE lid = " + listingId + " AND finished = 0": firstQuery;
        
        PrepFunction setFunc = (PreparedStatement prep) -> {
            prep.setInt(1, listingId);
        };

        ValueFunction valueFunc = (ResultSet resultSet) -> {
            List<String[]> dates = new ArrayList<>();
            while (resultSet.next()) {
            	String[] dateRange = new String[4];
                dateRange[0] = resultSet.getString("start_date");
                dateRange[1] = resultSet.getString("end_date");
                dateRange[2] = String.valueOf(resultSet.getDouble("day_price"));
                dateRange[3] = String.valueOf(resultSet.getInt("availability_id"));
                dates.add(dateRange);
            }
            return dates;
        };

        return (List<String[]>) getDBValues(conn, setFunc, query, valueFunc, new ArrayList<>());
    }

    public static void printDateRanges(List<String[]> dateRanges, boolean showPrice) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-mm-dd");
        int count = 1;
        for (String[] dateRange : dateRanges) {
            String startDate = dateRange[0];
            String endDate = dateRange[1];
            String price = dateRange[2];
            
            LocalDate start = LocalDate.parse(startDate, formatter);
            LocalDate end = LocalDate.parse(endDate, formatter);
            
            System.out.println(count + ". From " + start + " To " + end + (showPrice ? ", Day Price: " + price : ""));
            count++;
        }
    }

    public static boolean checkDateInRange(String startDate, String endDate, List<String[]> dateRanges, boolean shouldInRange) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-mm-dd");
        LocalDate start = LocalDate.parse(startDate, formatter);
        LocalDate end = LocalDate.parse(endDate, formatter);

        for (String[] dateRange : dateRanges) {
            LocalDate rangeStart = LocalDate.parse(dateRange[0], formatter);
            LocalDate rangeEnd = LocalDate.parse(dateRange[1], formatter);

            boolean startInRange = start.isAfter(rangeStart) || start.isEqual(rangeStart);
            boolean endInRange = end.isBefore(rangeEnd) || end.isEqual(rangeEnd);

            if (shouldInRange && startInRange && endInRange) {
                return true;
            } else if (!shouldInRange && (start.isBefore(rangeStart) || end.isAfter(rangeEnd))) {
                return true;
            }
        }

        return false;
    }
    
    public static int getHostId(Connection conn, int listingId) {
        String query = "SELECT uid FROM Listing WHERE lid = ?";

        PrepFunction setFunc = (PreparedStatement prep) -> {
            prep.setInt(1, listingId);
        };

        ValueFunction valueFunc = (ResultSet resultSet) -> {
            if (resultSet.next())
                return resultSet.getInt("uid");
            return -1;
        };

        Integer hostId = (Integer) getDBValues(conn, setFunc, query, valueFunc, -1);
        if (hostId != null) {
            return hostId;
        } else {
            // Handle case when listingId is not found or host is null
            throw new RuntimeException("Host ID not found for listing with ID: " + listingId);
        }
    }
    
    public static int selectAvailability(Connection conn, Scanner sc, int listingId) {
    	List<String[]> availableDates = listAvailableDates(conn, listingId, false);
    	
    	if (availableDates.isEmpty()) {
    		System.out.println("No availabilities found for listing");
    		return -1;
    	}
    	
    	
    	System.out.println("0. Back");
    	printDateRanges(availableDates, true);
        System.out.print("Enter the number of the availability to select: ");
        int selectedAvailability = CommandLine.getIntInput(sc);
         
        if (selectedAvailability == 0) {
        	return -1;
        }
        
        if (selectedAvailability >= 1 && selectedAvailability <= availableDates.size()) {
            // SQL statement to delete the selected availability by its ID
        	System.out.println(Integer.parseInt(availableDates.get(selectedAvailability - 1)[3]));
            return Integer.parseInt(availableDates.get(selectedAvailability - 1)[3]);
        } else {
            System.out.println("Invalid availability ID selected.");
        }
        
        return -1;
    }
    
    public static void deleteAvailability(Connection conn, Scanner sc, int listingId) {
        // SQL query to fetch all available start and end dates from the Availability table
        System.out.println("Available times for listing");

        String query = "SELECT availability_id, start_date, end_date FROM Availability WHERE lid = ?";

        PrepFunction setFunc = (PreparedStatement prep) -> {
            prep.setInt(1, listingId);
        };
     
        ValueFunction valueFunc = (ResultSet resultSet) -> {
            int count = 1;
            
            System.out.println("0. Back");
            
        	List<Integer> availabilityIds = new ArrayList<>();
            while (resultSet.next()) {
            	count++;
                int availabilityId = resultSet.getInt("availability_id");
                String availableStartDate = resultSet.getString("start_date");
                String availableEndDate = resultSet.getString("end_date");
                System.out.println(count+ ". From " + availableStartDate + " To " + availableEndDate);
                availabilityIds.add(availabilityId);
            }
            return availabilityIds;
        };

        @SuppressWarnings("unchecked")
		List<Integer> availabilityIds = (List<Integer>) getDBValues(conn, setFunc, query, valueFunc, new ArrayList<>());
        if (!availabilityIds.isEmpty()) {
            System.out.print("Enter the number of the availability to delete: ");
            int selectedAvailability = CommandLine.getIntInput(sc);
             
            if (selectedAvailability == 0) {
            	return;
            }
            
            if (selectedAvailability >= 1 && selectedAvailability <= availabilityIds.size()) {
                // SQL statement to delete the selected availability by its ID
                String deleteQuery = "DELETE FROM Availability WHERE availability_id = ?";

                PrepFunction deleteSetFunc = (PreparedStatement prep) -> {
                    prep.setInt(1, availabilityIds.get(selectedAvailability - 1));
                };

                setDBValues(conn, deleteSetFunc, deleteQuery,
                            "Availability deleted successfully",
                            "Failed to delete availability");
            } else {
                System.out.println("Invalid availability ID selected.");
            }
        } else {
            System.out.println("No availabilities found for listing");
        }
    }
    
    public static void changeAvailibilityPrice(Connection conn, Scanner sc, int availbilityId) {
        System.out.print("Enter new price for the listing: ");
        double newPrice = CommandLine.getDoubleInput(sc);
        
        // SQL statement to update the price of the listing
        String updateQuery = "UPDATE Availability SET day_price = ? WHERE availability_id = ?";

        // Prepare the statement and set the new price and listingId as parameters
        PrepFunction setFunc = (PreparedStatement prep) -> {
            prep.setDouble(1, newPrice);
            prep.setInt(2, availbilityId);
        };

        // Execute the update SQL statement
        setDBValues(conn, setFunc, updateQuery,
                    "Price updated successfully",
                    "Failed to update price");
    }
    
    public static void createBooking(Connection conn, Scanner sc, User user, int listingId)  {
        List<String[]> availbleDates = listAvailableDates(conn, listingId, false);
        printDateRanges(availbleDates, false);
        
        if (availbleDates.isEmpty()) {
        	System.out.println("No availability for this listing.");
        	return;
        } else if (user == null) {
			System.out.println("Please log in to book listings.");
			return;
        }
        
        int hostId = getHostId(conn, listingId);
        
        System.out.println("Enter booking details:");

        String[] ranges = getDateRangeInput(sc, availbleDates, false);
        
        String startDateStr = ranges[0];
        String endDateStr = ranges[1];

        // Parse start and end dates as LocalDate objects
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-mm-dd");
        LocalDate startDate = LocalDate.parse(startDateStr, formatter);
        LocalDate endDate = LocalDate.parse(endDateStr, formatter);

        // Calculate the number of days stayed
        long numberOfDays = 1 + endDate.toEpochDay() - startDate.toEpochDay();

        // Retrieve the day price of the listing from the database
        double dayPrice = getDayPrice(conn, startDateStr, endDateStr);

        // Calculate the total price
        double totalPrice = dayPrice * numberOfDays;

        // SQL statement to insert the new booking into the Booking table
        String insertQuery = "INSERT INTO Booking (lid, start_date, end_date, price, finished, uid, host, card_number) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        PrepFunction setFunc = (PreparedStatement prep) -> {
            prep.setInt(1, listingId);
            prep.setString(2, startDateStr);
            prep.setString(3, endDateStr);
            prep.setDouble(4, totalPrice);
            prep.setInt(5, 0); // Assuming the booking is not finished yet
            prep.setInt(6, user.uid);
            prep.setInt(7, hostId);
            prep.setString(8, User.getCard(conn, user.uid));
        };

        setDBValues(conn, setFunc, insertQuery, "Booking successfully created", "Failed to create booking");
    }
    
    // Helper method to retrieve the day price of a listing
    public static double getDayPrice(Connection conn, String startDate, String endDate) {
        String query = "SELECT day_price FROM Availability WHERE start_date <= ? AND end_date >= ?";
        
        PrepFunction setFunc = (PreparedStatement prep) -> {
            prep.setString(1, startDate);
            prep.setString(2, endDate);
        };
        
        ValueFunction valueFunc = (ResultSet resultSet) -> {
            if (resultSet.next())
                return resultSet.getDouble("day_price");
            return null;
        };
        return (Double) getDBValues(conn, setFunc, query, valueFunc, -1);
        
    }
    
    public static void addAvailability(Connection conn, String startDate, String endDate, double dayPrice, int listingId) {
    	String insertQuery = "INSERT INTO Availability (lid, start_date, end_date, day_price) VALUES (?, ?, ?, ?)";

        PrepFunction setFunc = (PreparedStatement prep) -> {
            prep.setInt(1, listingId);
            prep.setString(2, startDate);
            prep.setString(3, endDate);
            prep.setDouble(4, dayPrice);
        };

        setDBValues(conn, setFunc, insertQuery, "Availability successfully created", "Failed to create availability");
    }
	
	public static void createAvailability(Connection conn, Scanner sc, int listingId) {
	  		
	        List<String[]> availableDates = listAvailableDates(conn, listingId, true);
	        System.out.println("Select a date outside of these date ranges:");
	        printDateRanges(availableDates, false);
	        
	        String[] ranges = getDateRangeInput(sc, availableDates, true);
	        
	        System.out.print("day price: ");
	        double dayPrice = CommandLine.getDoubleInput(sc);
	        
	        String startDate = ranges[0];
	        String endDate = ranges[1];

	        // SQL statement to insert the new availability into the Availability table
	        addAvailability(conn, startDate, endDate, dayPrice, listingId);
	}
	
    public static void deleteListing(Connection conn, int listingId) {
        // SQL statement to delete the listing by its ID
        String deleteQuery = "DELETE FROM Listing WHERE lid = ?";

        PrepFunction setFunc = (PreparedStatement prep) -> {
            prep.setInt(1, listingId);
        };

        setDBValues(conn, setFunc, deleteQuery, "Listing deleted successfully", "Failed to delete listing");
    }
    
    public static int selectListing(Connection conn, Scanner sc, int uid) {
        // SQL query to fetch listing details for the given user
        String query = "SELECT lid, type, address, city, country FROM Listing WHERE uid = ?";

        PrepFunction setFunc = (PreparedStatement prep) -> {
            prep.setInt(1, uid);
        };

        ValueFunction valueFunc = (ResultSet resultSet) -> {
            List<Integer> listingIds = new ArrayList<>();
            int count = 1;
            
            System.out.println("0. Back");
            while (resultSet.next()) {
                int listingId = resultSet.getInt("lid");
                String listingType = resultSet.getString("type");
                String address = resultSet.getString("address");
                String city = resultSet.getString("city");
                String country = resultSet.getString("country");
                listingIds.add(listingId);
                System.out.println(count + ". Listing Type: " + listingType + ", Address: " + address + ", City: " + city + ", Country: " + country);
                count++;
            }

            if (listingIds.isEmpty()) {
                System.out.println("No listings found for this user.");
                return -1;
            }

            System.out.print("Enter listing number to modify:");
            int selectedListingNumber = CommandLine.getIntInput(sc);
            
            if (selectedListingNumber == 0) {
            	return 0;
            }
            
            if (selectedListingNumber >= 1 && selectedListingNumber <= listingIds.size()) {
                return listingIds.get(selectedListingNumber - 1);
            } else {
                System.out.println("Invalid listing number. No listing will be modified.");
                return -1;
            }
        };

        int returnValue = (Integer) getDBValues(conn, setFunc, query, valueFunc, -1);

        if (returnValue == -1) {
            System.out.println("No listings to display");
        } 
        
        if (returnValue == 0) {
        	returnValue = -1;
        }

        return returnValue;
    }

    public static void removeListing(Connection conn, Scanner sc, int uid) {
        int selectedListingId = selectListing(conn, sc, uid);
        
        if (selectedListingId != -1) {
            // Delete the selected listing and related data (e.g., amenities, availability)
            deleteListing(conn, selectedListingId);
        }
    }

    @SuppressWarnings("unchecked")
	public static List<String[]> addressSearch(Connection conn, User user, String country, String city, String address) {
	    String query = "SELECT * FROM Listing WHERE country = ? AND city = ? AND address = ? AND uid != ?";
	    
		return (List<String[]>) Database.getDBValues(conn, 
	        prep -> {
	            prep.setString(1, country);
	            prep.setString(2, city);
	            prep.setString(3, address);
	            prep.setInt(4, user != null ? user.uid : -1);
	        }, 
	        query, 
	        (ResultSet resultSet) -> {
	        	List<String[]> resultList = new ArrayList<>();
	            while (resultSet.next()) {
	                String[] resultArray = {
                        Integer.toString(resultSet.getInt("lid")),
                        resultSet.getString("type"),
                        resultSet.getString("postal"),
                        resultSet.getString("address"),
                        Double.toString(resultSet.getDouble("latitude")),
                        Double.toString(resultSet.getDouble("longitude")),
                        resultSet.getString("city"),
                        resultSet.getString("country")
	                };
	                resultList.add(resultArray);
	            }

	            return resultList;
	        },
	        new ArrayList<>()
	    );
	    
    }
	
	public static String[] getValidAddress(Connection conn, Scanner sc) {

	    while (true) {
	        System.out.println("Enter the following details for a valid address: ");
	        
	        System.out.print("Country: ");
	        String country = sc.nextLine();

	        System.out.print("City: ");
	        String city = sc.nextLine();

	        System.out.print("Address: ");
	        String address = sc.nextLine();

	        // Check if the entered address, city, and country combination is unique
	        boolean isUnique = isAddressCityCountryUnique(conn, address, city, country);
	        
	        if (isUnique) {
	            return new String[]{address, city, country};
	        } else {
	            System.out.println("This combination already exists in the database. Please enter a unique address.");
	        }
	    }
	}

	public static boolean isAddressCityCountryUnique(Connection conn, String address, String city, String country) {
	    String query = "SELECT * FROM Listing WHERE address = ? AND city = ? AND country = ?";
	    
        try {
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, address);
            stmt.setString(2, city);
            stmt.setString(3, country);
            ResultSet addresses = stmt.executeQuery();
            boolean unique;
            if (addresses.next())
                unique = false;
            else 
                unique = true;
            stmt.close();
            return unique;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
	}
    
    public static void createListing(Connection conn, Scanner sc, int uid) {
        // Prompt user for input
        System.out.println("Enter listing details:");

        System.out.print("Type (e.g. house, apartment, room): ");
        String type = sc.nextLine();

        double latitude;
        do {
            System.out.print("Latitude (e.g., 12.345678): ");
            latitude = CommandLine.getDoubleInput(sc);
        } while (latitude > 90 || latitude < -90);
        double longitude;
        do {
            System.out.print("Longitude (e.g., 98.7654321): ");
            longitude = CommandLine.getDoubleInput(sc);
        } while (longitude > 180 || longitude < -180);

        String[] addrInfo = getValidAddress(conn, sc);
        String postalCode = getValidPostalCode(sc);

        String address = addrInfo[0];
        String city =  addrInfo[1];
        String country =  addrInfo[2];

        System.out.println("Enter a comma-separate list of offered amenities: ");
        List<String> amenities = Arrays.asList(sc.nextLine().toLowerCase().split(","));

        double base;
        if (type.equalsIgnoreCase("house"))
            base = 300.00;
        else if (type.equalsIgnoreCase("apartment") || type.equalsIgnoreCase("condo"))
            base = 200.00;
        else if (type.equalsIgnoreCase("room"))
            base = 100.00;
        else
            base = 80.00;
        base += (amenities.size() * 10.00);

        String topAmenities = "SELECT name, COUNT(name) AS num FROM Amenity "
                            + "GROUP BY name ORDER BY COUNT(name) DESC";

        String insertQuery = "INSERT INTO Listing (type, postal, address, latitude, longitude, city, country, uid) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try {
            String suggestStr = "Suggested amenities: ";
            Statement suggest = conn.createStatement();
            ResultSet rs = suggest.executeQuery(topAmenities);
            int count = 0;
            while (rs.next() && count < 5) {
                String curr = rs.getString("name");
                if (amenities.contains(curr))
                    continue;
                suggestStr += curr + ", ";
                count++;
            }
            suggest.close();
            if (count != 0)
                System.out.println(suggestStr.substring(0, suggestStr.length() - 2));

            PreparedStatement stmt = conn.prepareStatement(insertQuery);
            stmt.setString(1, type);
            stmt.setString(2, postalCode);
            stmt.setString(3, address);
            stmt.setDouble(4, latitude);
            stmt.setDouble(5, longitude);
            stmt.setString(6, city);
            stmt.setString(7, country);
            stmt.setInt(8, uid);
            stmt.executeUpdate();
            stmt.close();

            Statement getID = conn.createStatement();
            rs = getID.executeQuery("SELECT LAST_INSERT_ID() FROM Listing");
            rs.next();
            int lid = rs.getInt(1);
            getID.close();

            insertQuery = "INSERT INTO Amenity (name, lid) VALUES (?, ?)";
            stmt = conn.prepareStatement(insertQuery);
            for (String amenity : amenities) {
                stmt.setString(1, amenity.trim());
                stmt.setInt(2, lid);
                stmt.executeUpdate();
            }
            stmt.close();
            System.out.println("[Created Listing #" + lid + ", suggesting listing price: $" + base + "]");
        } catch (SQLException e) {
            System.err.println("Failed creating listing...");
            e.printStackTrace();
        }    
	}
	
	public static int setDBValues(Connection conn, PrepFunction setFunc, String insertQuery, String successMessage, String failureMessage) {
		 
		try (PreparedStatement ps = conn.prepareStatement(insertQuery, PreparedStatement.RETURN_GENERATED_KEYS)) {
	        setFunc.apply(ps);
	
	        // Execute the SQL statement to insert the new user
	        int rowsAffected = ps.executeUpdate();
	        if (rowsAffected > 0) {
	        	if (successMessage != "") {
	                System.out.println(successMessage);
	        	}
	
	            ResultSet generatedKeys = ps.getGeneratedKeys();
	            if (generatedKeys.next()) {
	                return generatedKeys.getInt(1);
	            }
	        } else {
	        	if (failureMessage != "") {
	                System.out.println(failureMessage);
	        	}
	        }
	    } catch (SQLException e) {
	    	e.printStackTrace();
	    }
		return -1;
	}

    public static List<Integer> displayBookings(Connection conn, int id_set, String hostOrRenter) {
        String setQuery = "SELECT bid, start_date, end_date, price FROM Booking WHERE " + hostOrRenter + " = ? AND finished = 0";
        
        PrepFunction setFunc = (PreparedStatement prep) -> {
            prep.setInt(1, id_set);
        };

        ValueFunction valueFunc = (ResultSet resultSet) -> {
            System.out.println("0. Back");
        	
        	List<Integer> bookingIds = new ArrayList<>();
            int count = 1;
            
            while (resultSet.next()) {
                int bookingId = resultSet.getInt("bid");
                String startDate = resultSet.getString("start_date");
                String endDate = resultSet.getString("end_date");
                double price = resultSet.getDouble("price");
                bookingIds.add(bookingId); 
                System.out.println(count + 
                        ", Start Date: " + startDate +
                        ", End Date: " + endDate + ", Price: " + price);
                count++;
            }
        	return bookingIds;
        };
		@SuppressWarnings("unchecked")
		List<Integer> bookingIds = (List<Integer>) getDBValues(conn, setFunc, setQuery, valueFunc, new ArrayList<>());
        
        return bookingIds;
    }

    public static String[] getDateRangeInput(Scanner sc, List<String[]> dateRanges, boolean checkOverlapping) {

        while (true) {
            System.out.println("Enter a valid start date (YYYY-MM-DD):");
            String startDate = sc.nextLine();
            System.out.println("Enter a valid end date (YYYY-MM-DD):");
            String endDate = sc.nextLine();

            try {
                LocalDate.parse(startDate);
                LocalDate.parse(endDate);
            } catch (DateTimeParseException e) {
                System.out.println("Invalid date format. Please use the format YYYY-MM-DD.");
                continue;  // Retry input
            }

            if ((!isDateInRange(startDate, endDate, dateRanges) && !checkOverlapping) ||
                (!doDatesNotOverlap(startDate, endDate, dateRanges) && checkOverlapping) ||
                startDate.compareTo(endDate) > 0 ||
                !isValidDateRange(startDate, true)) {
                System.out.println("Invalid date range. Please enter a valid start and end date.");
                continue;  // Retry input
            }

            return new String[]{startDate, endDate};
        }
    }
    
    public static boolean doDatesNotOverlap(String startDate, String endDate, List<String[]> dateRanges) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-mm-dd");
        LocalDate start = LocalDate.parse(startDate, formatter);
        LocalDate end = LocalDate.parse(endDate, formatter);

        for (String[] dateRange : dateRanges) {
            LocalDate rangeStart = LocalDate.parse(dateRange[0], formatter);
            LocalDate rangeEnd = LocalDate.parse(dateRange[1], formatter);
            
            if (start.isEqual(rangeEnd) || start.isEqual(rangeStart) || end.isEqual(rangeEnd) || end.isEqual(rangeStart))
            	return false;
            if (start.isBefore(rangeEnd) && end.isAfter(rangeStart))
                return false; // Overlapping dates found
        }
        return true; // No overlapping dates found
    }
    

    public static boolean isDateInRange(String startDate, String endDate, List<String[]> dateRanges) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-mm-dd");
        LocalDate start = LocalDate.parse(startDate, formatter);
        LocalDate end = LocalDate.parse(endDate, formatter);

        for (String[] dateRange : dateRanges) {
            LocalDate rangeStart = LocalDate.parse(dateRange[0], formatter);
            LocalDate rangeEnd = LocalDate.parse(dateRange[1], formatter);

            if ((!start.isBefore(rangeStart) || start.isEqual(rangeStart)) &&
                (!end.isAfter(rangeEnd) || end.isEqual(rangeEnd))) {
                return true;
            }
        }

        return false;
    }

    private static boolean isValidDateRange(String date, boolean afterToday) {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-mm-dd");
            Date inputDate = dateFormat.parse(date);
            Date today = new Date();
            
            if (afterToday)
                return inputDate.after(today);
            return inputDate.before(today);
        } catch (ParseException e) {
            return false;
        }
    }

	public static void cancelBooking(Connection conn, Scanner sc, User user) {
		int choice = -1;
		do {
			System.out.println("0. Back");
	        System.out.println("1. Cancel a booking you are a host of");
	        System.out.println("2. Cancel a booking you rented");
	        System.out.print("Choose one of the previous options: ");

			choice = CommandLine.getIntInput(sc);
			switch (choice) {
			case 1:
				Database.deleteBooking(conn, sc, user.uid, "host");
				break;
			case 2:
				Database.deleteBooking(conn, sc, user.uid, "uid");
				break;
			default:
				break;
            }
		} while (choice != 0);
	}

    public static void deleteBooking(Connection conn, Scanner sc, int id_set, String hostOrRenter) {
        List<Integer> bookingIds = displayBookings(conn, id_set, hostOrRenter);
        
        if (bookingIds.isEmpty()) {
        	System.out.println("No bookings found");
        	return;
        }
        
        System.out.print("Enter the number of the booking you want to cancel: ");
        int selectedBookingNumber = CommandLine.getIntInput(sc);
        
        if (selectedBookingNumber == 0)
        	return;
        
        if (selectedBookingNumber >= 1 && selectedBookingNumber <= bookingIds.size()) {
            int selectedBookingId = bookingIds.get(selectedBookingNumber - 1);
            
            int finishStatus = 0;
            if (hostOrRenter == "host")
            	finishStatus = 1;
            else
            	finishStatus = 2;
            
            String updateQuery = "UPDATE Booking SET finished = " + finishStatus + " WHERE bid = ?";
            PrepFunction setFunc = ps -> ps.setInt(1, selectedBookingId);

            setDBValues(conn, setFunc, updateQuery, "Booking was canceled successfully.", "Failed to cancel booking.");

            try {
                String cancel = "INSERT INTO Cancellation(bid, cancel_date) VALUES (" + selectedBookingId + ", CURDATE())";
                Statement stmt = conn.createStatement();
                stmt.executeUpdate(cancel);
            } catch (SQLException e) {
                System.err.println("Failed to record cancellation...");
                e.printStackTrace();
                return;
            }
        } else {
            System.out.println("Invalid booking number. No booking will be deleted.");
        }
    }
    
    public static String getValidPostalCode(Scanner sc) {
        String postalCode;
        do {
            System.out.print("Enter a valid postal code: ");
            postalCode = sc.nextLine();

            if (!CommandLine.isValidPostalCode(postalCode))
                System.out.println("Invalid postal code. Please enter a valid format (e.g., A1A1A1).");
        } while (!CommandLine.isValidPostalCode(postalCode));

        return postalCode;
    }

}
