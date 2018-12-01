package game.user;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.Vector;

import packet.Packet;
import util.Database;

public class MissionData {
	public int m_dwMissionID;
	public HashMap<Integer, Integer> m_mapCompletionInfo;
	public int m_tmRegDate;
	public int m_tmEndDate;
	public int m_nInitSubCnt;
	
	public MissionData() {
		m_dwMissionID = 0;
		m_mapCompletionInfo = new HashMap<Integer, Integer>();
		m_mapCompletionInfo.clear();
		m_tmRegDate = 0;
		m_tmEndDate = 0;
		m_nInitSubCnt = 0;
	}
	
	
	// 미션 불러오기
	public static void LoadMyMissions(GameUser u) {
		if( null == u.Missions )
			u.Missions = new Vector<MissionData>();
		
		u.Missions.clear();
		
		Connection con = Database.getConnection();
		try {
			PreparedStatement ps = con.prepareStatement("SELECT * FROM `mission` WHERE `LoginUID` = ?");
			ps.setInt(1, u.LoginUID);
			ResultSet rs = ps.executeQuery();
			if( rs.first() == true ) {
				do {
					MissionData temp_mission = new MissionData();
					
					temp_mission.m_dwMissionID = rs.getInt("MissionItemID");
					temp_mission.m_tmRegDate = rs.getInt("RegDate");
					temp_mission.m_tmEndDate = rs.getInt("EndDate");
					
					u.Missions.add(temp_mission);
				}while( rs.next() == true );
			}
			
			rs.close();
			ps.close();
			con.close();
		}catch(Exception e) {
			e.printStackTrace(); return;
		}
	}
	
	public void write_MissionInfoPacket(Packet p) {
		p.writeInt(m_dwMissionID);
		
		p.writeInt(m_mapCompletionInfo.size());
		for( int i=0; i < m_mapCompletionInfo.size(); i++ ) {
			Set<Integer> keyset = m_mapCompletionInfo.keySet();
			Iterator<Integer> it = keyset.iterator();
			
			Integer key = it.next();
			
			p.write( key ); // Map은 앞의 키도 같이 보내야한다.
			p.write( m_mapCompletionInfo.get(key) );
		}
		
		p.writeInt(m_tmRegDate);
		p.writeInt(m_tmEndDate);
		p.writeInt(m_nInitSubCnt);
	}
}
