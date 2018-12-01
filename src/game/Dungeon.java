package game;

import game.user.GameUser;
import packet.OpcodeGame;
import packet.Packet;

public class Dungeon {

	public static void EVENT_DUNGEON_REWARD_EXP_REQ(GameUser u, Packet p) {
		int triggerID = p.readInt();
		
		// KEVENT_DUNGEON_REWARD_EXP_ACK
		/*
		typedef std::map< DWORD, KCharExpReward >   KEVENT_DUNGEON_REWARD_EXP_ACK;  // [UserUID, ExpInfo]
		typedef std::map< DWORD, KCharExpReward >   KEVENT_PVP_REWARD_EXP_GP_ACK;      // [UserUID, ExpInfo]
		
		KCharExpReward:
	char        m_cCharType;    // 캐릭터 타입
    __int64     m_biExp;        // 레벨값을 제외하고 계산된 경험치
    __int64     m_biTotalExp;   // 누적 경험치(전체 경험치)
    DWORD       m_dwOldLevel;   // 이전 레벨
    DWORD       m_dwLevel;      // 현재 레벨
    float       m_fRewardExp;   // 획득 경험치
    KSkillInfo  m_kSkillInfo;   // 스킬정보
    KSkillInfo  m_kSkillTreeInfo; // 스킬정보, 스킬각성
		 */
		
		// 귀찮으니 map 0보낸다
		
		Packet pr = new Packet(OpcodeGame.EVENT_DUNGEON_REWARD_EXP_ACK);
		pr.writeInt(0);
		u.parent.sendPacket(pr, true);
	}

	public static void EVENT_SPECIAL_REWARD_REQ(GameUser u, Packet p) {
		if( u.CurrentRoom == null )
			return;
		
		int sizeRewardInfo = p.readInt();

		// 귀찮으니 걍 그 방에 있는 전부한테 보상을 주자
		Packet pr = new Packet(OpcodeGame.EVENT_SPECIAL_REWARD_BROAD);
		pr.writeInt( u.CurrentRoom.GetPlayerCount() ); // std::vector<KRewardInfo>    m_vecSpecialReward;
		
		for( int i=0; i<6; i++ ) {
			if( u.CurrentRoom.slots[i].isActive == true ) {
				pr.writeInt(u.CurrentRoom.slots[i].client.getGameUser().LoginUID);
				pr.write(0); // char type
				pr.writeInt(1); // set<USHORT> index
				pr.writeShort(0); // index
				pr.writeInt(0); // dropgp
				pr.writeInt(0); // currentgp
				pr.writeInt(0); // vector<KItem>
				pr.writeInt(0); // vector<KRewardBox>
			}
		}
		
		for( int i=0; i<6; i++ )
			if( u.CurrentRoom.slots[i].isActive == true )
				u.CurrentRoom.slots[i].client.sendPacket(pr, true);
		
	}
}
