/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package library.management.system;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JOptionPane;


/**
 *
 * @author CHRISZEL
 */
public class LibraryDB {
    private static final String JDBC_URL = "jdbc:mysql://localhost:3306/library_db";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "";
    private LibraryDB() {} // Prevent instantiation

    // =================================================================
    // 1. CONNECTION METHOD
    // =================================================================

    public static Connection getConnection() throws SQLException {
        try {
            // Note: Class.forName() may be optional depending on your JDBC driver version
            // Class.forName("com.mysql.cj.jdbc.Driver"); 
            return DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASSWORD);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, 
                "Database Connection Error. Please check your config and server status.\nError: " + e.getMessage(), 
                "Database Error", JOptionPane.ERROR_MESSAGE);
            throw e;
        }
    }

    // =================================================================
    // 2. BOOK MANAGEMENT (LibraryGUI2 & LibraryGUI5)
    // =================================================================
    
    /**
     * Retrieves books based on their archive status. Used for both Active and Archived views.
     */
    public static List<BookManager.Book> getBooksByArchiveStatus(boolean isArchived) {
        List<BookManager.Book> books = new ArrayList<>();
        String sql = "SELECT book_no, title, author, genre, subject, grade, available_quantity FROM Books WHERE is_archived = ? ORDER BY book_no";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setBoolean(1, isArchived);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    books.add(new BookManager.Book(
                        rs.getString("book_no"),
                        rs.getString("title"),
                        rs.getString("author"),
                        rs.getString("genre"),
                        rs.getString("subject"),
                        rs.getString("grade"),
                        rs.getInt("available_quantity")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching books: " + e.getMessage());
        }
        return books;
    }
    
    /**
     * Adds a new book to the database.
     */
    public static void addBook(String bookNo, String title, String author, String genre, String subject, String grade, int quantity) {
        String sql = "INSERT INTO Books (book_no, title, author, genre, subject, grade, total_quantity, available_quantity, is_archived) VALUES (?, ?, ?, ?, ?, ?, ?, ?, FALSE)";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, bookNo);
            pstmt.setString(2, title);
            pstmt.setString(3, author);
            pstmt.setString(4, genre);
            pstmt.setString(5, subject);
            pstmt.setString(6, grade);
            pstmt.setInt(7, quantity);
            pstmt.setInt(8, quantity); // available_quantity starts equal to total_quantity
            
            pstmt.executeUpdate();
            JOptionPane.showMessageDialog(null, "Book added successfully!");
            
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "Error adding book: " + e.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Updates book details. Note: The logic for updating total/available quantity must be sound.
     */
    public static void updateBook(String bookNo, String title, String author, String genre, String subject, String grade, int totalQuantity, int availableQuantity) {
        String sql = "UPDATE Books SET title=?, author=?, genre=?, subject=?, grade=?, total_quantity=?, available_quantity=? WHERE book_no=?";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, title);
            pstmt.setString(2, author);
            pstmt.setString(3, genre);
            pstmt.setString(4, subject);
            pstmt.setString(5, grade);
            pstmt.setInt(6, totalQuantity);
            pstmt.setInt(7, availableQuantity);
            pstmt.setString(8, bookNo);
            
            pstmt.executeUpdate();
            JOptionPane.showMessageDialog(null, "Book updated successfully.");
            
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "Error updating book: " + e.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Archives a book (sets is_archived to TRUE).
     */
    public static void archiveBook(String bookNo) {
        String sql = "UPDATE Books SET is_archived = TRUE WHERE book_no = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, bookNo);
            pstmt.executeUpdate();
            
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "Error archiving book: " + e.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Retrieves a book from the archive (sets is_archived to FALSE).
     */
    public static void retrieveBook(String bookNo) {
        String sql = "UPDATE Books SET is_archived = FALSE WHERE book_no = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, bookNo);
            pstmt.executeUpdate();
            
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "Error retrieving book: " + e.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // =================================================================
    // 3. BORROWING/RETURNING (LibraryGUI3)
    // =================================================================
    
    /**
     * Handles the complex transaction of borrowing a book.
     */
    public static void recordBorrow(String borrowerId, String bookNo, java.util.Date borrowDate, java.util.Date dueDate) {
        String insertRecordSql = "INSERT INTO Borrow_Records (borrower_id, book_no, date_borrowed, due_date) VALUES (?, ?, ?, ?)";
        String updateBookSql = "UPDATE Books SET available_quantity = available_quantity - 1 WHERE book_no = ?";
        
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false); // Start transaction

            // 1. Insert Borrow Record
            try (PreparedStatement recordPstmt = conn.prepareStatement(insertRecordSql)) {
                recordPstmt.setString(1, borrowerId);
                recordPstmt.setString(2, bookNo);
                recordPstmt.setDate(3, new java.sql.Date(borrowDate.getTime()));
                recordPstmt.setDate(4, new java.sql.Date(dueDate.getTime()));
                recordPstmt.executeUpdate();
            }

            // 2. Decrease Book Quantity
            try (PreparedStatement bookPstmt = conn.prepareStatement(updateBookSql)) {
                bookPstmt.setString(1, bookNo);
                bookPstmt.executeUpdate();
            }

            conn.commit(); // Commit transaction
            JOptionPane.showMessageDialog(null, "Book successfully borrowed. Transaction committed.");
            
        } catch (SQLException e) {
            System.err.println("Error recording borrow: " + e.getMessage());
            if (conn != null) {
                try {
                    conn.rollback(); // Rollback transaction on error
                } catch (SQLException ex) {
                    System.err.println("Rollback failed: " + ex.getMessage());
                }
            }
            JOptionPane.showMessageDialog(null, "Transaction Failed. Please check logs. Error: " + e.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (SQLException e) { /* Ignore close error */ }
            }
        }
    }
    
    /**
     * Records a book return and updates available quantity.
     */
    public static void recordReturn(int recordId, String bookNo, double finalPenalty) {
        String returnSql = "UPDATE Borrow_Records SET date_returned = CURDATE(), penalty = ? WHERE record_id = ?";
        String updateBookSql = "UPDATE Books SET available_quantity = available_quantity + 1 WHERE book_no = ?";
        
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false); // Start transaction

            // 1. Update Borrow Record
            try (PreparedStatement returnPstmt = conn.prepareStatement(returnSql)) {
                returnPstmt.setDouble(1, finalPenalty);
                returnPstmt.setInt(2, recordId);
                returnPstmt.executeUpdate();
            }

            // 2. Increase Book Quantity
            try (PreparedStatement bookPstmt = conn.prepareStatement(updateBookSql)) {
                bookPstmt.setString(1, bookNo);
                bookPstmt.executeUpdate();
            }

            conn.commit(); // Commit transaction
            JOptionPane.showMessageDialog(null, "Book returned successfully!");
            
        } catch (SQLException e) {
            System.err.println("Error recording return: " + e.getMessage());
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { /* handle */ }
            }
            JOptionPane.showMessageDialog(null, "Return Transaction Failed: " + e.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (SQLException e) { /* Ignore close error */ }
            }
        }
    }


    // =================================================================
    // 4. REPORTS (LibraryGUI4)
    // =================================================================
    
    /**
     * Retrieves all currently overdue records. 
     * NOTE: This returns a ResultSet and requires the GUI layer to iterate and close the connection/statement.
     */
    public static ResultSet getOverdueReports(Connection conn) throws SQLException {
        // Use the connection passed in, allowing the GUI to manage the lifespan of the ResultSet
        String sql = "SELECT T2.id_number, T2.first_name, T2.last_name, T3.title, T1.book_no, T1.due_date, " +
                     "DATEDIFF(CURDATE(), T1.due_date) AS Days_Late, T1.penalty " +
                     "FROM Borrow_Records T1 " +
                     "JOIN Borrowers T2 ON T1.borrower_id = T2.id_number " +
                     "JOIN Books T3 ON T1.book_no = T3.book_no " +
                     "WHERE T1.date_returned IS NULL AND T1.due_date < CURDATE() " +
                     "ORDER BY Days_Late DESC";
                     
        Statement stmt = conn.createStatement();
        return stmt.executeQuery(sql);
    }
    
    /**
     * Retrieves the full borrowing history.
     */
    public static ResultSet getFullBorrowHistory(Connection conn) throws SQLException {
        String sql = "SELECT T2.first_name, T2.last_name, T3.title AS Book_Title, T1.date_borrowed, T1.date_returned " +
                     "FROM Borrow_Records T1 " +
                     "JOIN Borrowers T2 ON T1.borrower_id = T2.id_number " +
                     "JOIN Books T3 ON T1.book_no = T3.book_no " +
                     "ORDER BY T1.date_borrowed DESC";

        Statement stmt = conn.createStatement();
        return stmt.executeQuery(sql);
    }
}


