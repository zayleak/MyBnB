import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.InputMismatchException;
import java.util.List;
import java.util.Scanner;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;

public class CommandLine {

	private static final String dbClassName = "com.mysql.cj.jdbc.Driver";
	private static final String CONNECTION = "jdbc:mysql://127.0.0.1/";

	private Connection conn = null;
	private Scanner sc = null;

	public boolean startSession() {
		boolean success = true;
		if (sc == null)
			sc = new Scanner(System.in);
		try {
			success = connect(this.getCredentials());
		} catch (ClassNotFoundException e) {
			success = false;
			System.err.println("Establishing connection triggered an exception!");
			e.printStackTrace();
			sc = null;
		}
		return success;
	}
	
    /* Function that acts as destructor of an instance of this class.
     * Performs some housekeeping setting instance's private field
     * to null
     */
	public void endSession() {
		try {
			conn.close();
		} catch (SQLException e) {
			System.err.println("Exception occured while disconnecting!");
			e.printStackTrace();
		} finally {
			conn = null;
		}

		if (sc != null)
			sc.close();
		sc = null;
	}

    /* Function that executes an infinite loop and activates the respective 
     * functionality according to user's choice. At each time it also outputs
     * the menu of core functionalities supported from our application.
     */
	public boolean execute() {
		if (sc == null || conn == null) {
			System.out.println("");
			System.out.println("Connection could not been established! Bye!");
			System.out.println("");
			return false;
		}
		
		int choiceStart = -1;
		do {
			startMenu(); //Print Menu
			choiceStart = getIntInput(sc);
			switch (choiceStart) {
			case 1:
				Util.loadStructure(conn, "./db/structure.sql");
				break;
			case 2:
				Util.loadData(conn, "./db/sample.txt");
				break;
			case 3:
				int choiceHome = -1;
				do {
					User user = null;
					homeMenu(); //Print Menu
					choiceHome = getIntInput(sc);
					switch (choiceHome) {
					case 1:
						if ((user = User.newUser(conn, sc)) != null)
							System.out.println("*** Created new account ***");
						else 
							break;
					case 2:
						if (user == null && (user = User.login(conn, sc)) == null) {
							System.err.println("*** Login failed ***");
							break;
						}
						int choiceUser = -1;
						do {
							userMenu(user);
							choiceUser = getIntInput(sc);
							switch (choiceUser) {
							case 1: // Create a Listing
								Database.createListing(conn, sc, user.uid);
								break;
							case 2: // Cancel a Booking
								Database.cancelBooking(conn, sc, user);
								break;
							case 3: // Remove a Listing
								Database.removeListing(conn, sc, user.uid);
								break;
							case 4: // Search for a Listing
								manageQuerySearch(conn, sc, user);
								break;
							case 5: // Update Price of a Listing
								int selectedListingId = Database.selectListing(conn, sc, user.uid);
								if (selectedListingId == -1)
									break;
								int selectedAvailability = Database.selectAvailability(conn, sc, selectedListingId);
								if (selectedAvailability == -1)
									break;
								Database.changeAvailibilityPrice(conn, sc, selectedAvailability);
								break;
							case 6: // Insert Comments
								handleComments(conn, sc, user.uid);
								break;
							case 7: // Update/delete availability
								modifyAvalibility(conn, sc, user.uid);
								break;
							case 9:
								if (User.delete(conn, sc, user.uid))
									choiceUser = 0;
							default:
								break;
							}
						} while (choiceUser != 0);
						user = null;
						System.out.println("Logged out successfully.");
						break;
					case 3:
						manageQuerySearch(conn, sc, user);
						break;
					case 4:
						int choiceReport = -1;
						do {
							reportMenu();
							choiceReport = getIntInput(sc);
							switch (choiceReport) {
							case 1:
								Report.numBookings(conn, sc);
								break;
							case 2:
								Report.numListings(conn);
								break;
							case 3:
								Report.hostListingRanking(conn);
								break;
							case 4:
								Report.renterBookingRanking(conn, sc);
								break;
							case 5:
								Report.mostCancellations(conn);
								break;
							case 6:
								Report.possibleCommercialHosts(conn);
								break;
							case 7:
								Report.listingPopularWords(conn);
								break;
							default:
								break;
							}
						} while (choiceReport != 0);
						break;
					default:
						break;
					}
				} while (choiceHome != 0);
				break;
			default:
				break;
			}
		} while (choiceStart != 0);
		return true;
	}



	public void modifyAvalibility(Connection conn, Scanner sc, int loggedInId) {
		int selectedListing;
		int choice = -1;
		do {
			System.out.println("0. Back");
	        System.out.println("1. Delete an avalibility for a listing");
	        System.out.println("2. Add an avalibility for a listing");
			System.out.print("Choose one of the previous options: ");

	        choice = getIntInput(sc);
	        switch (choice) {
			case 1:
				selectedListing = Database.selectListing(conn, sc, loggedInId);
				if (selectedListing == -1)
					return;
				Database.deleteAvailability(conn, sc, selectedListing);
				break;
			case 2:
				selectedListing = Database.selectListing(conn, sc, loggedInId);
				if (selectedListing == -1)
					return;
				Database.createAvailability(conn, sc, selectedListing);
				break;
			default:
				break;
	        }
	    } while (choice != 0);
	}

	public void handleComments(Connection conn, Scanner sc, int uid) {
		int choice = -1;
		do {
			System.out.println("0. Back");
            System.out.println("1. Add a comment to a host you rented from");
            System.out.println("2. Add a comment to a renter who rented from you");
            System.out.println("3. Add a comment to a listing you stayed at");
            System.out.print("Choose one of the previous options: ");

            choice = getIntInput(sc);
            switch (choice) {
			case 1:
				Database.displayAndSelectBooking(conn, sc, uid, "host");
				break;
			case 2:
				Database.addCommentToRenter(conn, sc, uid);
				break;
			case 3:
				Database.displayAndSelectBooking(conn, sc, uid, "listing");
				break;
			default:
				break;
            }
        } while (choice != 0);
    }
	
	public static void handleFullQuery(Connection conn, Scanner sc, String query, User user) {

		@SuppressWarnings("unchecked")
		List<String[]> getResults = (List<String[]>) Database.getDBValues(conn, prep -> {}, query, (ResultSet resultSet) -> {
        	List<String[]> resultList = new ArrayList<>();
    
            while (resultSet.next()) {
                String[] resultArray = {
					Integer.toString(resultSet.getInt("lid")),
					resultSet.getString("type"),
					resultSet.getString("postal"),
					resultSet.getString("address"),
					Double.toString(resultSet.getDouble("latitude")),
					Double.toString(resultSet.getDouble("latitude")),
					resultSet.getString("city"),
					resultSet.getString("country"),
					Double.toString(resultSet.getDouble("price"))
                };
                resultList.add(resultArray);
            }

            return resultList;
        }, new ArrayList<>());
		

		if (getResults.isEmpty()) {
			System.out.println("No results found for query");
			return;
		}

		int selectedId = selectListingAll(sc, getResults);
		if (selectedId == 0) {
			return;
		}

		Database.createBooking(conn, sc, user, selectedId);
	}
	
	public static int selectListingAll(Scanner sc, List<String[]> resultList) {

	    System.out.println("Select a listing:");
	    System.out.println("0. Back");
	    for (int i = 0; i < resultList.size(); i++) {
	        String[] result = resultList.get(i);
	        System.out.println((i + 1) + ". Listing Type: " + result[1] +
	                ", Postal Code: " + result[2] +
	                ", Address: " + result[3] +
	                ", Latitude: " + result[4] +
	                ", Longitude: " + result[5] +
	                ", City: " + result[6] +
	                ", Country: " + result[7] +
	                (result.length == 9 ? ", Minimum Price: " + result[8]: ""));
	    }

	    int selectedOption;
	    do {
	        System.out.print("Enter the number of the listing you want to view: ");
	        selectedOption = getIntInput(sc);
	    } while (selectedOption < 0 || selectedOption > resultList.size());
	    
	    if (selectedOption == 0) {
	    	return 0;
	    }
	    
	    // Subtracting 1 to get the correct index in the resultList
	    return Integer.parseInt(resultList.get(selectedOption - 1)[0]);
	}
	
	public static void manageQuerySearch(Connection conn, Scanner sc, User user) {
	    int choice = -1;
		do {
	        System.out.println("0. Back");
	        System.out.println("1. Search by address");
	        System.out.println("2. Perform a full query search");
			System.out.print("Choose one of the previous options: ");
			
	        choice = getIntInput(sc);
	        switch (choice) {
			case 1: // Call a method to handle search by address
				handleAddressSearch(conn, sc, user);
				break;
			case 2: // Call a method to handle full query search
				fullQuerySearch(conn, sc, user);
				break;
			default:
				break;
	        }
	    } while (choice != 0);
	}
	
	public static void handleAddressSearch(Connection conn, Scanner sc, User user) {
	    System.out.println("Enter the following details for address search:");
	    
	    System.out.print("Country: ");
	    String country = sc.nextLine();
	    
	    System.out.print("City: ");
	    String city = sc.nextLine();
	    
	    System.out.print("Address: ");
	    String address = sc.nextLine();
	    
	    List<String[]> searchResults = Database.addressSearch(conn, user, country, city, address);
	    
	    if (searchResults.isEmpty()) {
	        System.out.println("No results found for the provided address.");
	        return;
	    }

	    int selectedId = selectListingAll(sc, searchResults);
	    if (selectedId == 0) {
	        return;
	    }

	    Database.createBooking(conn, sc, user, selectedId);
	}
	
	public static void fullQuerySearch(Connection conn, Scanner sc, User user) {
	    DecimalFormat decimalFormat = new DecimalFormat("#.##");
	    double latitude = -1, prevLatitude = -1;
	    double longitude = -1, prevLongitude = -1;
	    double distance = 10, prevDistance = 10;
	    String priceSortOrder = "none"; // Default value for price sort order
	    String sortBy = "distance"; // Default value for sort by
	    String postalCode = "none", prevPostal = "none"; // Default value for postal code
	    List<String> amenities = new ArrayList<>(); // Default value for amenities
	    String startDate = "none", prevStart = "none";
	    String endDate = "none", prevEnd = "none";
	    Double lowerPrice = -1.0, prevLower = -1.0;
	    Double higherPrice = -1.0, prevHigher = -1.0;
	    
	    // Ask for user preferences
	    int option;
	    do {
	        System.out.println("Specify search parameters (If you enter nothing in these it will not be provided in search):");
	        System.out.println("0. Back");
			System.out.println("1. Latitude, Longitude (Current value: Latitude: " + (latitude != -1 ? latitude : "none") + ", Longitude: " + (longitude != -1 ? longitude : "none") + ")");
	        System.out.println("2. Specify Maximum distance away from location in km (Current value: " + (distance != -1 ? distance : "none") + ")");
	        System.out.println("3. Rank by Price (ascending/descending) (Current value: " + priceSortOrder + ")");
	        System.out.println("4. Postal Code (Current value: " + postalCode + ")");
	        System.out.println("5. Price Range (Current value: " +  negNone(lowerPrice) + "-" + negNone(higherPrice) + " )");
	        System.out.println("6. Date Range (Current value: " + startDate + "-" + endDate + " )");
	        System.out.println("7. Set of Amenities (Current value: " + amenities + ")");
	        System.out.println("8. Perform Search");
	        System.out.print("Choose one of the previous options: ");
	        int sortOrderOption;
	        
	        try {
	            option = getIntInput(sc);
	            switch (option) {
				case 0: 
					return;
				case 1:
					System.out.println("Current value: Latitude: " + (latitude != -1 ? latitude : "none")
										+ ", Longitude: " + (longitude != -1 ? longitude : "none"));

					prevLatitude = latitude;
					prevLongitude = longitude;
					
					do {
						System.out.print("Enter latitude (e.g., 12.345678): ");
						latitude = CommandLine.getDoubleInput(sc);
					} while (latitude > 90 || latitude < -90);
					do {
						System.out.print("Enter longitude (e.g., 98.7654321): ");
						longitude = CommandLine.getDoubleInput(sc);
					} while (longitude > 180 || longitude < -180);
					
					prevLatitude = latitude;
					prevLongitude = longitude;
				
					break;
				case 2:
					System.out.println("Distance: " + (distance != -1 ? distance : "none"));
					System.out.print("Enter distance (in km): ");
					prevDistance = distance;
					distance = getDoubleInput(sc);
					prevDistance = distance;
					break;
				case 3:
					System.out.print("Rank by price ascending (1) or descending (2): ");
					sortOrderOption = getIntInput(sc);
					if (sortOrderOption == 1) {
						priceSortOrder = "ascending";
					} else if (sortOrderOption == 2) {
						priceSortOrder = "descending";
					} else {
						System.out.println("Invalid input. Please enter a valid option.");
					}
					break;
				case 4:
					System.out.print("Search by postal code: ");
					prevPostal = postalCode;
					postalCode = sc.next();
					if (!isValidPostalCode(postalCode)) {
						System.out.println("Invalid postal code format. Current value will be used.");
						postalCode = prevPostal;
					}
					prevPostal = postalCode;
					break;
				case 5:
					System.out.print("Enter the lower price range: ");
					prevLower = lowerPrice;
					prevHigher = higherPrice;
					lowerPrice = getDoubleInput(sc);
					if (lowerPrice < 0) {
						System.out.println("Invalid price range. Prices cannot be negative.");
						lowerPrice = prevLower;
						break;
					}
					System.out.print("Enter the higher price range: ");
					higherPrice = getDoubleInput(sc);

					if (higherPrice < 0) {
						System.out.println("Invalid price range. Prices cannot be negative.");
						lowerPrice = prevLower;
						higherPrice = prevHigher;
					} else if (lowerPrice >= higherPrice) {
						System.out.println("Invalid price range. The lower price must be less than the higher price.");
						lowerPrice = prevLower;
						higherPrice = prevHigher;
					}
					higherPrice = Double.parseDouble(decimalFormat.format(higherPrice));
					lowerPrice = Double.parseDouble(decimalFormat.format(lowerPrice));
					prevHigher = higherPrice;
					prevLower = lowerPrice;
					break;
				case 6:
					System.out.print("Start date (yyyy-mm-dd): ");
					SimpleDateFormat sdf = new SimpleDateFormat("yyyy-mm-dd");
					prevStart = startDate;
					prevEnd = endDate;

					startDate = sc.nextLine();
					Date start = sdf.parse(startDate);
					if (!isValidDate(startDate)) {
						System.out.println("Invalid date format. Current value will be used.");
						startDate = prevStart;
						break;
					}
					Calendar calendar = Calendar.getInstance();
					calendar.setTime(new Date());
					calendar.add(Calendar.DAY_OF_MONTH, -1);
					Date previousDate = calendar.getTime();
					if (start.before(previousDate)) {
						System.out.println("Invalid start date");
						startDate = prevStart;
						break;
					}

					System.out.print("End date (yyyy-mm-dd): ");
					endDate = sc.nextLine();
					Date end = sdf.parse(endDate);
					if (!isValidDate(endDate)) {
						System.out.println("Invalid date format. Current value will be used.");
						startDate = prevStart;
						endDate = prevEnd;
					}
					if (start.after(end)) {
						System.out.println("Invalid end date");
						startDate = prevStart;
						endDate = prevEnd;
					}

					prevStart = startDate;
					prevEnd = endDate;
					break;
				case 7:
					System.out.println("Current amenities: " + amenities);
					System.out.println("Enter a comma-separate list of amenities: ");
					amenities = Arrays.asList(sc.nextLine().toLowerCase().split(","));
					break;
				default:
					break;
	            }
	            
	        } catch (InputMismatchException | ParseException e) {
	            System.out.println("Invalid input. Please enter a valid option.");
	            sc.nextLine(); // Consume the invalid input
	            option = -1; // Reset option to continue the loop
	            latitude = prevLatitude;
	            longitude = prevLongitude;
	            distance = prevDistance;
	            startDate = prevStart;
	            endDate = prevEnd;
	            higherPrice = prevHigher;
	            lowerPrice = prevLower;
	            postalCode = prevPostal;
	            
	        }
	    } while (option != 8);
	    	    
	    String query = constructQuery(latitude, longitude, distance, priceSortOrder, sortBy, postalCode, lowerPrice, higherPrice, startDate, endDate, amenities,  user != null ? user.uid : -1);
	    handleFullQuery(conn, sc, query, user);
	}

	public static boolean isValidPostalCode(String postalCode) {
		return postalCode.matches("[A-Za-z]\\d[A-Za-z]\\d[A-Za-z]\\d");
	}

	public static boolean isValidDate(String date) {
		try {
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-mm-dd");
			sdf.parse(date);
		} catch (ParseException e) {
			return false;
		}
		return true;
	}

	private static String constructQuery(double latitude, double longitude, double distance, String priceSortOrder,
			String sortBy, String postalCode, Double lowerPrice, Double higherPrice,
			String startDate, String endDate, List<String> amenities, int excludedUserId) {

		StringBuilder queryBuilder = new StringBuilder();
		queryBuilder.append("SELECT * FROM (")
					.append("SELECT L.*, (6371 * ACOS(COS(RADIANS(")
					.append(latitude)
					.append(")) * COS(RADIANS(L.latitude)) * COS(RADIANS(L.longitude) - RADIANS(")
					.append(longitude)
					.append(")) + SIN(RADIANS(")
					.append(latitude)
					.append(")) * SIN(RADIANS(L.latitude)))) AS distance, ");

		// Subquery to get the minimum price for each listing in Availability
		queryBuilder.append("(SELECT MIN(A.day_price) FROM Availability A WHERE A.lid = L.lid");

		if (!"none".equals(startDate))
			queryBuilder.append(" AND A.start_date <= '").append(endDate).append("'")
						.append(" AND A.end_date >= '").append(startDate).append("'");


		queryBuilder.append(") AS price ");
		queryBuilder.append("FROM Listing L");

		boolean whereAdded = false;

		if (latitude != -1 && longitude != -1 && distance != -1) {
			queryBuilder.append(whereAdded ? " AND" : " HAVING")
						.append(" distance <= ")
						.append(distance);
			whereAdded = true;
		}

		if (!"none".equals(postalCode)) {
			queryBuilder.append(whereAdded ? " AND" : " WHERE").append(" postal LIKE '").append(postalCode.substring(0, 5)).append("%'");
			whereAdded = true;
		}

		if (lowerPrice != -1.0 && higherPrice != -1.0) {
			queryBuilder.append(whereAdded ? " AND" : " HAVING").append(" price BETWEEN ").append(lowerPrice).append(" AND ").append(higherPrice);
			whereAdded = true;
		}

		// Exclude listings with the specified uid
		if (excludedUserId != -1) {
			queryBuilder.append(whereAdded ? " AND" : " WHERE").append(" uid <> ").append(excludedUserId);
			whereAdded = true;
		}

		if (amenities != null && !amenities.isEmpty()) {
			queryBuilder.append(whereAdded ? " AND" : " WHERE").append(" lid IN (SELECT lid FROM Amenity WHERE name IN (");
			int count = amenities.size();
			for (String amenity : amenities) {
				queryBuilder.append("'").append(amenity.trim()).append("'");
				count--;
				if (count > 0) {
					queryBuilder.append(", ");
				}
			}
			queryBuilder.append(") GROUP BY lid HAVING COUNT(DISTINCT name) = ").append(amenities.size()).append(")");
		}

		if (!"none".equals(priceSortOrder)) {
			queryBuilder.append(" ORDER BY");
			if (latitude != -1) {
				queryBuilder.append(" distance, price"); // Always sort by distance closest
			} else {
				queryBuilder.append(" price"); // Sort by day_price when latitude is not provided
			}
			queryBuilder.append(" ").append("ascending".equals(priceSortOrder) ? "ASC" : "DESC");
		} else if (latitude != -1) {
			queryBuilder.append(" ORDER BY distance"); // Default sorting by distance when no priceSortOrder is provided
		}

		queryBuilder.append(") AS Subquery WHERE price IS NOT NULL");

		return queryBuilder.toString();
	}
	
    // Called during the initialization of an instance of the current class
    // in order to retrieve from the user the credentials with which our program
    // is going to establish a conn with MySQL
	private String[] getCredentials() {
		String[] cred = new String[3];
		cred[0] = "root";
		cred[1] = "zehi@mysql";
		cred[2] = "mydb";
		return cred;
	}

    // Initialize current instance of this class.
	private boolean connect(String[] cred) throws ClassNotFoundException {
		Class.forName(dbClassName);
		boolean success = true;
		String user = cred[0];
		String pass = cred[1];
		String connection = CONNECTION + cred[2];
		try {
			conn = DriverManager.getConnection(connection, user, pass);
		} catch (SQLException e) {
			success = false;
			System.err.println("Connection could not be established!");
			e.printStackTrace();
		}
		return success;
	}

	//Print menu options
	private static void startMenu() {
		System.out.println("======================");
		System.out.println("0. Exit");
		System.out.println("1. Initialize empty DB");
		System.out.println("2. Load dummy data");
		System.out.println("3. Proceed");
		System.out.print("Choose one of the previous options: ");
	}

	private static void homeMenu() {
		System.out.println("=========HOME=========");
		System.out.println("0. Back");
		System.out.println("1. Create account");
		System.out.println("2. Log in");
		System.out.println("3. Find listings");
		System.out.println("4. Get reports");
		System.out.print("Choose one of the previous options: ");
	}

	private static void userMenu(User user) {
		System.out.println("=========MENU=========");
		System.out.println("Logged in as " + user.username);
		System.out.println("0. Log Out");
		System.out.println("1. Create a Listing");
		System.out.println("2. Cancel a Booking");
		System.out.println("3. Remove a Listing");
		System.out.println("4. Search for a Listing");
		System.out.println("5. Update Price of an availability for a Listing");
		System.out.println("6. Insert Comments");
		System.out.println("7. Modify availability of listing");
		System.out.println("9. Delete Account");
		System.out.print("Choose one of the previous options: ");
	}

	private static void reportMenu() {
		System.out.println("========REPORT========");
		System.out.println("0. Back");
		System.out.println("1. # of bookings");
		System.out.println("2. # of listings");
		System.out.println("3. Host ranking by # of listings");
		System.out.println("4. Renter ranking by # of bookings");
		System.out.println("5. Users with most cancellations");
		System.out.println("6. Possible commercial hosts");
		System.out.println("7. Popular noun phrases per listing");
		System.out.print("Choose one of the previous options: ");
	}

    public static int getIntInput(Scanner sc) {
        while (true) {
            try {
                return Integer.parseInt(sc.nextLine());
            } catch (NumberFormatException e) {
                System.out.print("Error on input. Please enter a valid integer.");
            }
        }
    }

    public static double getDoubleInput(Scanner sc) {
        while (true) {
            try {
                return Double.parseDouble(sc.nextLine());
            } catch (NumberFormatException e) {
                System.out.println("Error on input. Please enter a valid decimal number.");
            }
        }
    }

	public static String negNone(Double value) {
        if (Double.compare(value, -1.0) == 0)
            return "none";

        DecimalFormat decimalFormat = new DecimalFormat("#.##");
        return decimalFormat.format(value);
    }

}
