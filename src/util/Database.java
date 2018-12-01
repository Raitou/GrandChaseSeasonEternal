package util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class Database {
	public static Connection getConnection() {
		try {
            Class.forName("com.mysql.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        try {
            Connection con = DriverManager.getConnection(Ini.getIni("db.host"), Ini.getIni("db.user"), Ini.getIni("db.pass"));
            return con;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
	}
	
	public static boolean test() {
		try {
			Connection c = getConnection();
			c.close();
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}
	
	public static void close(Connection con, PreparedStatement ps, ResultSet rs) {
		if( rs != null )
			try { rs.close(); } catch(Exception e) { }
		
		if( ps != null )
			try { ps.close(); } catch(Exception e) { }
		
		if( con != null )
			try { con.close(); } catch(Exception e) { }
	}
}
