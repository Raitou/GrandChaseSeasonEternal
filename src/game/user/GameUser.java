package game.user;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.Vector;

import client.GameClient;
import game.Channel;
import game.GameFunction;
import game.Room;
import game.Square;
import game.guild.GuildInfo;
import game.guild.GuildUserInfo;
import game.item.KItem;
import game.item.PetInfo;
import packet.OpcodeGame;
import packet.Packet;
import server.Main;
import util.Convert;
import util.Database;
import util.Ini;

public class GameUser {
	public GameClient parent = null;
	
	// 플레이어 구성요소들
	public String ID = null;
	public String PW = null;
	public int LoginUID = -1;
	public int AuthLevel = 0;
	public String Nick = null;
	
	public int CoupleUID = 0;
	public Vector<KItem> CoupleEquip;
	
	public int GamePoint = 0;
	public int InvenCapacity = 0;
	public int BonusPoint = 0;
	public int SpecialBonusPoint = 0;
	public int VirtualCashPoint = 0;
	public int AttendTime = 0;
	public int AttendPoint = 0;
	public int PremiumType = 0; // 0일반 1GC클럽 2GC블로그 4카니발입장 8골드스테이지
	
	public Vector<GameCharacter> Characters = null; // CharIndex(Type)
	public Vector<MissionData> Missions = null;
	public Vector<PetInfo> Pets = null; // 원래 맵인데 걍 벡터 쓴다.
	public HashMap<Long, KItem> Items = null; // ItemUID
	public GuildInfo Guild = null;
	
	// 옵션?
	public boolean boolDenyInvite = false;
	public boolean isPCBang = false;
	public byte PCBangType = 0;
	
	// 채널, 방
	public Channel CurrentChannel = null;
	public int CurrentChannelNo = -1;
	public Room CurrentRoom = null;
	public int CurrentRoomNo = -1;
	public Square CurrentSquare = null; public float squarePosX = 0; public float squarePosY = 0;
	
	public int CurrentChar = 0;
	public int CurrentPromotion = 0;
	
	public String chatColor = null; // 채팅 색깔
	public boolean isFirstConnect = false; // 채널 입장(캐릭터선택)시 공지 전송용
	
	// 확성기 제한용
	public long lastMegaphone = 0;
	
	// 생성자
	public GameUser(GameClient p) {
		parent = p;
		
		Characters = new Vector<GameCharacter>();
		Missions = new Vector<MissionData>();
		Pets = new Vector<PetInfo>();
		Items = new HashMap<Long, KItem>();
		Guild = new GuildInfo();
		
		CoupleEquip = new Vector<KItem>();
	}

	@SuppressWarnings("unused")
	public void EVENT_VERIFY_ACCOUNT_REQ(Packet p) {
		ID = p.readStringWithLength();
		PW = p.readStringWithLength();
		String GetIP = p.readUnicodeStringWithLength();
		byte sex = p.readByte();
		int ProtocolVersion = p.readInt();
		int P2PVersion = p.readInt();
		int MainChecksum = p.readInt();
		int ConnectType = p.readInt(); // 초기접속 | 서버이동
		int Age = p.readInt();
		int AuthType = p.readInt();
		int AuthTick = p.readInt();
		byte ExpAccount = p.readByte(); // 체험계정
		String CountryCode = p.readUnicodeStringWithLength();
		int FunBoxBonus = p.readInt();
		int m_nLinBonus = p.readInt();
		int m_dwChannellingType = p.readInt();
		int m_nUniqueKey = p.readInt();
		long m_biUniqueKey = p.readLong();
		int m_nLanguageCode = p.readInt();
		
		// 로그인
		Connection con = Database.getConnection();
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			ps = con.prepareStatement("SELECT * FROM account WHERE Login = ? AND Passwd = ?");
			ps.setString(1, ID);
			ps.setString(2, PW);
			rs = ps.executeQuery();
			
			// 로그인 실패
			if( rs.first() == false ) {
				Main.printmsg("<" + this.parent.server.serverName + "> 로그인에 실패하였습니다. ID: " + ID);
				
				Packet pLoginfail = new Packet(OpcodeGame.EVENT_VERIFY_ACCOUNT_ACK);
				pLoginfail.writeInt(1); // 로그인 실패
				pLoginfail.writeStringWithLength(ID);
				pLoginfail.writeInt(0);
				parent.sendPacket(pLoginfail, true);
				
				rs.close();
				ps.close();
				con.close();
				return;
			}
			
			LoginUID = rs.getInt("LoginUID");
			Nick = rs.getString("Nick");
			AuthLevel = rs.getInt("AuthLevel");
			GamePoint = rs.getInt("GamePoint");
			VirtualCashPoint = rs.getInt("VP");
			InvenCapacity = rs.getInt("InvenCapacity");
			BonusPoint = rs.getInt("BonusPoint");
			SpecialBonusPoint = rs.getInt("SpecialBonusPoint");
		
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			Database.close(con, ps, rs);
		}
		
		// 닉네임 필터링
		//Nick = Nick.replace("#c", "＃c");
		
		Main.printmsg("<" + this.parent.server.serverName + "> 로그인 성공! ID: " + ID);
		
		// 로딩
		Main.printmsg("        - 아이템 로딩 중..");
		KItem.LoadMyItems(this); // 아이템을 제일 먼저 로딩 해야함
		
		Main.printmsg("        - 캐릭터 로딩 중..");
		GameCharacter.LoadMyCharacters( this );
		
		Main.printmsg("        - 미션 로딩 중..");
		MissionData.LoadMyMissions(this);
		
		Main.printmsg("        - 펫 로딩 중..");
		PetInfo.LoadMyPets(this);
		
		// 먼저 전송할것
		GameFunction.Send_ExpTable(this);
		
		Main.printmsg("        - 인벤토리 전송 중..");
		KItem.Send_EVENT_VERIFY_INVENTORY_NOT(this);
		
		// UserPacket.cpp / 125 line
		Packet pLogin = new Packet(OpcodeGame.EVENT_VERIFY_ACCOUNT_ACK);

		pLogin.writeUnicodeStringWithLength(ID);
		pLogin.writeUnicodeStringWithLength(Nick);
		pLogin.write(0); // ucOK
		pLogin.writeInt(GamePoint);
		pLogin.writeHexString("E0 04 A9 40 10 04 A9 60");
		pLogin.write(1); // sex
		pLogin.writeIntLE( Convert.IPToInt(parent.s.getInetAddress().getHostAddress()) ); 
		GuildUserInfo.write_NoGuildUserInfoPacket(pLogin);
		pLogin.write(AuthLevel); // AuthLevel
		pLogin.writeInt(100); // age
		pLogin.write(0); // 개인정보 동의 체크
		pLogin.write(0); // PC방
		
		// 캐릭터 패킷
		pLogin.writeInt(Characters.size());
		for( int i=0; i < Characters.size(); i++ ) {
			pLogin.write(i); // map이라 인덱스 보내야함
			Characters.get(i).write_CharacterInfoPacket( pLogin );
		}
		
		pLogin.writeShort( (short) 9600 ); // udp port
		pLogin.writeInt( LoginUID );
		pLogin.writeUnicodeStringWithLength( parent.server.serverName );
		pLogin.writeInt(1); // 신규유저,초기접속,재접속 여부
		
		pLogin.writeInt( parent.server.serverMessage.size() );
		for( int i=0; i < parent.server.serverMessage.size(); i++ ) {
			pLogin.writeUnicodeStringWithLength( parent.server.serverMessage.get(i) );
		}
		//pLogin.writeInt(1);
		//pLogin.writeUnicodeStringWithLength("플지컴이당~");
		
		DungeonUserInfo.write_mapDifficulty(pLogin);
		pLogin.writeInt(0x12F376D3); // m_nConnectType
		
		// 미션 패킷
		pLogin.writeInt(Missions.size());
		for( int i=0; i < Missions.size(); i++ )
			Missions.get(i).write_MissionInfoPacket( pLogin );

		pLogin.writeInt( parent.server.serverTypeFlag ); // m_dwServerType
		pLogin.writeInt(0); // RP
		pLogin.writeInt(0); // RP rank
		pLogin.writeInt(0x80); // AuthType
		
		// 메시지서버
		pLogin.writeInt(0); // server uid
		pLogin.writeInt(0); // server part
		pLogin.writeUnicodeStringWithLength( Ini.getIni("game.msgserver.name") );
		pLogin.writeStringWithLength( Ini.getIni("game.msgserver.ip") );
		pLogin.writeShort( Short.parseShort(Ini.getIni("game.msgserver.port")) );
		pLogin.writeInt(0); // user num
		pLogin.writeInt(0); // max user num
		pLogin.writeInt(0); // 프로토콜
		pLogin.writeInt(-1); // pair-left 레벨범위
		pLogin.writeInt(-1); // pair-right 레벨범위
		pLogin.writeStringWithLength( Ini.getIni("game.msgserver.ip") ); // 전달용
		pLogin.writeUnicodeStringWithLength( "" ); // 서버 설명
		pLogin.writeInt(0); // max level
		
		pLogin.write(3); // m_cRecommendUser
		pLogin.writeInt(0x57F173AC); // m_tFirstLoginTime
		pLogin.writeInt(0x57F173AC); // m_tLastLoginTime
		pLogin.writeInt(0); // m_nPubEvent
		
		// 펫
		pLogin.writeInt( Pets.size() ); // 크기
		for( int i=0; i < Pets.size(); i++ ) {
			pLogin.writeLong( Pets.get(i).m_dwUID ); // 원래 맵이라 인덱스를 보낸다
			Pets.get(i).write_PetInfoPacket(pLogin);
		}
		
		pLogin.writeInt(0); // vector<integer> m_vecExpiredMission
		pLogin.write(1); // m_bEnableNewTermEvent
		pLogin.writeInt(InvenCapacity);
		pLogin.writeInt(PremiumType); // m_kPremiumInfo
		pLogin.writeInt(BonusPoint);
		pLogin.writeInt(SpecialBonusPoint);
		pLogin.write(0); // m_bIsRecommendEvent
		pLogin.write(1); // m_bCheckChanneling
		pLogin.writeInt(1); // m_dwChannelType
		pLogin.writeInt(0x61D0B2C0); // m_tVirtualEnableDate
		pLogin.write(0); // m_cUserBenfitType
		pLogin.writeHexString("64 7E EE E2 C0 07 E7 10 6B 7C 92 A0 00 00 00 00 A4 72 93 E0 57 EF 5E F0");
		
		pLogin.writeInt(0); // KEVENT_SEALED_CHARACTER_INFO_NOT m_kSealedCharInfoNot
		pLogin.writeInt(20); // ? 캐릭터 수
		for( int i=0; i < 20; i++ ) {
			pLogin.writeInt(i); 
			pLogin.writeInt(i); // 두번보내는거 보아 map인듯
			pLogin.writeInt(0);
			pLogin.writeInt(0);
			pLogin.writeShort( (short) 0 );
		}
		pLogin.writeInt(2); //  std::vector< std::pair< int, GCITEMID > > m_vecGachaUseVersions
		pLogin.writeInt(30);
		pLogin.writeInt(779510);
		pLogin.writeInt(31);
		pLogin.writeInt(1404170);
		pLogin.writeInt(400); // m_nLookItemInvenCapacity
		pLogin.write(0); // m_bTutorialEnable
		
		parent.sendPacket(pLogin, true);
		
		Main.printmsg("        - 로그인 성공.");
		
		// 정보 전송
		GameFunction.Send_ServerTime(this);
		GameFunction.Send_PetVestedItem(this);
		GameFunction.Send_GraduateCharInfo(this);
		GameFunction.Send_JumpingCharInfo(this);
		GameFunction.Send_SlotInfo(this);
		GameFunction.Send_GuideCompleteInfo(this);
		GameFunction.Send_FullLookInfo(this);
		Main.printmsg("        - 정보 전송 성공.");

		// 다른 사람들한테 로긴을 알려주장
		int onlineusernum = parent.server.clients.size();
		for( int i=0; i<onlineusernum; i++ ) {
			GameClient target = (GameClient)parent.server.clients.get(i);
			
			if( target != null )
				target.getGameUser().SendChat("플지컴서버", "", "#cFFFF00" + Nick + "님께서 접속하였습니다! (현재 접속자: " + onlineusernum + "명)#cX");
		}
	}
	
	public void EVENT_CHAR_SELECT_JOIN_REQ(Packet p) {
		Main.printmsg("<" + this.parent.server.serverName + "> 캐릭터 선택 및 변경 ID: " + ID);
		
		GameFunction.Send_NonInvenItemList(this); //Main.printmsg("        - 정보 전송 완료 1");
		GameFunction.Send_StrengthMaterialInfo(this); //Main.printmsg("        - 정보 전송 완료 2");
		GameFunction.Send_WeeklyrewardList(this); //Main.printmsg("        - 정보 전송 완료 3");
		GameFunction.Send_MonthlyrewardList(this); //Main.printmsg("        - 정보 전송 완료 4");
		GameFunction.Send_EVENT_LOAD_POINTSYSTEM_INFO_ACK(this); //Main.printmsg("        - 정보 전송 완료 5");
		GameFunction.Send_MatchRankReward(this); //Main.printmsg("        - 정보 전송 완료 6");
		GameFunction.Send_HeroDungeonInfo(this); //Main.printmsg("        - 정보 전송 완료 7");
		GameFunction.Send_UserWeaponChange(this); //Main.printmsg("        - 정보 전송 완료 8");
		GameFunction.Send_NewCharCardInfo(this); //Main.printmsg("        - 정보 전송 완료 9");
		GameFunction.Send_VirtualCashLimitRatio(this); //Main.printmsg("        - 정보 전송 완료 10");
		GameFunction.Send_BadUserInfo(this); //Main.printmsg("        - 정보 전송 완료 11");
		GameFunction.Send_CollectionMission(this); //Main.printmsg("        - 정보 전송 완료 12");
		GameFunction.Send_HellTicketFreeMode(this); //Main.printmsg("        - 정보 전송 완료 13");
		GameFunction.Send_VIPItemList(this); //Main.printmsg("        - 정보 전송 완료 14");
		GameFunction.Send_CapsuleList(this); //Main.printmsg("        - 정보 전송 완료 15");
		GameFunction.Send_MissionPackList(this); //Main.printmsg("        - 정보 전송 완료 16");
		GameFunction.Send_VirtulCash(this); //Main.printmsg("        - 정보 전송 완료 17");
		GameCharacter.Send_EVENT_USER_CHANGE_WEAPON_NOT( this ); //Main.printmsg("        - 정보 전송 완료 18");
		
		if( isFirstConnect == false ) {
			isFirstConnect = true;
			
			int onlineusernum = parent.server.clients.size();
			SendSignBoard("플지컴서버", Nick, 
					  "▶ 플레이지씨컴 접속을 환영합니다! " +
			          "▶ 현재 " + onlineusernum + "명이 접속중입니다. " +
			          "▶ '/명령어'를 입력하여 명령어를 확인하세요. " +
			          "▶ 빛속성 '린'을 사용하기 위해서는 어둠 스킬의 첫번째 스킬을 삭제(-)해주세요. "
			          );
		}
	}
	
	public GameCharacter getCharacter(int cType) { return getCharacter( (byte)cType ); }
	public GameCharacter getCharacter(byte cType) {
		GameCharacter c = null;
		
		for(int i=0; i<Characters.size(); i++) {
			if( Characters.get(i).m_cCharType == cType ) {
				c = Characters.get(i);
				break;
			}
		}
		
		// 없는 캐릭터를 요청했음..
		if( c == null ) {
			Main.printmsg("없는 캐릭터를 요청했습니다... 임시로 만들어줌..\n" +
		                  "아이디: " + ID + "\n" +
		                  "캐릭터: " + cType + "\n", 0);
			
			c = new GameCharacter();
			c.m_cCharType = cType;
			
			Characters.add(c);
		}
		
		return c;
	}
	
	public void SendChat(String 보낸이, String 받는이, String 내용) {
		SendChat(보낸이, 받는이, 내용, 0xFFFFFF);
	}

	public void SendChat(String 보낸이, String 받는이, String 내용, int 색) {
		// 외치기/일반/파티/귓/공지/무조건귓/색깔채팅/커플/영자/팀/팀색깔/길드

		Packet pChat = new Packet(OpcodeGame.EVENT_CHAT_NOT);
		pChat.write(8); // 운영자 채팅으로 하면 #c 필터링을 안함
		pChat.writeInt(-1);
		pChat.writeUnicodeStringWithLength(보낸이);
		pChat.writeInt(-1);
		pChat.writeUnicodeStringWithLength(받는이);
		pChat.writeInt(색);
		pChat.writeUnicodeStringWithLength(내용);
		pChat.writeInt(0);
		pChat.writeInt(0);
		
		parent.sendPacket(pChat, false);
	}
	
	public void SendSignBoard(String 보낸이, String 받는이, String 내용) {
		Packet pChat = new Packet(OpcodeGame.EVENT_SIGN_BOARD_NOT);
		pChat.writeInt(4); // 전광판 타입 (광장, 서버, 광장영자, 서버영자, 서버인겜)
		pChat.writeInt(0); // 보낸이UID
		pChat.writeUnicodeStringWithLength(보낸이); // 보낸이
		pChat.writeInt(0); // 아이템 id
		pChat.writeInt(0);
		pChat.writeUnicodeStringWithLength(내용);
		
		parent.sendPacket(pChat, false);
	}

	public static void EVENT_USE_CHANGE_NICKNAME_REQ(GameUser u, Packet p) {
		int nOK = p.readInt();
		int ItemID = p.readInt();
		long ItemUID = p.readLong();
		String oldNick = p.readUnicodeStringWithLength();
		String newNick = p.readUnicodeStringWithLength();
		boolean bUseItem = p.readBool();
		
		// 닉네임 카드 체크는 나중에 만들자
		
		boolean isSuccess = false;
		
		Connection con = Database.getConnection();
		PreparedStatement ps = null;
		ResultSet rs = null;
		
		try {
			ps = con.prepareStatement("SELECT * FROM account WHERE Nick = ?");
			ps.setString(1, newNick);
			rs = ps.executeQuery();
			
			// 없는 닉네임이다.
			if( rs.first() == false ) {					
				isSuccess = true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			Database.close(null, ps, rs);
		}
		
		Packet pr = new Packet(OpcodeGame.EVENT_USE_CHANGE_NICKNAME_ACK);

		if( isSuccess == true ) {
			// 닉네임을 바꿔준다
			try {
				ps = con.prepareStatement("UPDATE `account` SET `Nick` = ? WHERE `Login` = ? AND `Nick` = ?");
				ps.setString(1, newNick);
				ps.setString(2, u.ID);
				ps.setString(3, u.Nick);
				ps.executeUpdate();
				ps.close();
				
				ps = con.prepareStatement("INSERT INTO `log_nickname` (`Date`, `Login`, `OldNick`, `NewNick`) VALUES (now(), ?, ?, ?)");
				ps.setString(1, u.ID);
				ps.setString(2, u.Nick);
				ps.setString(3, newNick);
				ps.executeUpdate();
				ps.close();
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				//Database.close(null, ps, rs);
			}
			
			// 서버에서도 닉넴이 바뀐걸 기억해야지
			u.Nick = newNick;
			
			pr.writeInt(0);
		} else {
			// 닉네임이 이미 있다 ㅡㅡ
			pr.writeInt(-2);
		}
		
		Database.close(con, ps, rs);
		
		pr.writeUnicodeStringWithLength(newNick);
		pr.writeLong(0); // GCITEMUID m_UseItemUID; // 사용한 닉네임 변경 카드
		u.parent.sendPacket(pr, false);
	}

	public static void EVENT_USE_BONUS_POINT_REQ(GameUser u, Packet p) {
		Packet pr = new Packet(OpcodeGame.EVENT_USE_BONUS_POINT_ACK);
		pr.writeInt(0);
		pr.writeInt(u.BonusPoint);
		pr.writeInt(u.SpecialBonusPoint);
		u.parent.sendPacket(pr, false);
	}
	
	public void LeaveCh() {
		// 채널에 들어가있으면 나가게 만들어주자
		Set<Integer> ChKey = parent.server.serverCh.keySet();
		Iterator<Integer> it = ChKey.iterator();
		while( it.hasNext() ) {
			int ChNo = it.next();
			if( parent.server.serverCh.get(ChNo).vecClients.contains(this.parent) == true )
				parent.server.serverCh.get(ChNo).vecClients.remove(this.parent);
		}
		
		CurrentChannel = null;
		CurrentChannelNo = -1;
	}
}
