package msg;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import client.MsgClient;
import packet.OpcodeMsg;
import packet.Packet;
import util.Database;

public class MsgFunction {

	public static void EMS_S2_VERIFY_ACCOUNT_REQ(MsgClient c, Packet p) {
	    int m_dwUserUID = p.readInt();
	    String m_strLogin = p.readUnicodeStringWithLength();
	    String m_strNickName = p.readUnicodeStringWithLength();
	    String m_strServerName = p.readUnicodeStringWithLength();
	    String m_strLocation = p.readUnicodeStringWithLength();
	    int m_iLocationNum = p.readInt();
	    boolean m_bGamming = p.readBool();
	    int m_nGuildID = p.readInt();
	    int m_dwChannelType = p.readInt();
	    int m_nLanguageCode = p.readInt();
	    String m_strCharNickName = p.readUnicodeStringWithLength();
		
		// 로그인
		Connection con = Database.getConnection();
		PreparedStatement ps = null;
		ResultSet rs = null;
		
		try {
			ps = con.prepareStatement("SELECT * FROM account WHERE Login = ? AND LoginUID = ?");
			ps.setString(1, m_strLogin);
			ps.setInt(2, m_dwUserUID);
			rs = ps.executeQuery();
			
			// 로그인 실패
			if( rs.first() == false ) {
				Packet pLoginfail = new Packet(OpcodeMsg.EMS_S2_VERIFY_ACCOUNT_ACK);
				pLoginfail.writeInt(-1); // 로그인 실패
				pLoginfail.writeInt(0);
				KGroup.write_NoKGroupPacket(pLoginfail, 1);
				KGroup.write_NoKGroupPacket(pLoginfail, 3);
				pLoginfail.writeBool(false);
				pLoginfail.writeInt(0);

				c.sendPacket(pLoginfail, true);
				
				Database.close(con, ps, rs);
				return;
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			Database.close(con, ps, rs);
		}
		
		c.UserUID = m_dwUserUID;
		c.UserID = m_strLogin;
		c.UserNick = m_strNickName;
		c.UserServerName = m_strServerName;
		c.UserLocation = m_strLocation;
		c.UserLocationNum = m_iLocationNum;
		c.UserIsGamming = m_bGamming;
		c.UserGuildID = m_nGuildID;
		
		Packet pr = new Packet(OpcodeMsg.EMS_S2_VERIFY_ACCOUNT_ACK);
		pr.writeInt(0);
		
		// std::map<int,KGroup> m_mapFriendGroup
		
		
		KGroup.write_NoKGroupPacket(pr, 1);
		KGroup.write_NoKGroupPacket(pr, 3);
		pr.writeBool(false);
		pr.writeInt(0);

		c.sendPacket(pr, true);
	}

}
