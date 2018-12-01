package game;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.Vector;

import client.GameClient;
import game.user.DungeonUserInfo;
import game.user.GameUser;
import packet.Crypto;
import packet.OpcodeGame;
import packet.Packet;
import server.Server;
import util.Convert;

public class Channel {
	public static final int MAX_ROOMS = 30000; // 방 최대 개수
	public static final int CH_TYPE_PVP = 3;
	public static final int CH_TYPE_DUNGEON = 0;
	public static final int CH_NUM_PVP = 1;
	public static final int CH_NUM_DUNGEON = 6;
	
	public Server parentServer = null;
	
	public Vector<GameClient> vecClients = null; // 접속 클라이언트
	public HashMap<Short, Room> mapRooms = null; // 방
	
	public Channel(Server parent) {
		parentServer = parent;
		
		vecClients = new Vector<GameClient>();
		mapRooms = new HashMap<Short, Room>();
	}
	
	public short getEmptyRoomID() {
		for( short i=0; i<MAX_ROOMS; i++ ) {
			if( !mapRooms.containsKey(i) ) // 방번호가 없다
				return i;
		}
		return -1;
	}
	
	public static void EVENT_ENTER_CHANNEL_REQ(GameUser u, Packet p) {
		// 일단 채널을 나가준다
		u.LeaveCh();
		
		// 채널입장
		int ch = p.readInt();
		
		int chtype = -1;
		
		switch( ch ) {
		case CH_NUM_PVP: // 대전
			chtype = CH_TYPE_PVP;
			break;
		case CH_NUM_DUNGEON: // 던전
			chtype = CH_TYPE_DUNGEON;
			break;
		default: // 실패
			ch = -1;
			break;
		}
		
		// 채널 객체
		u.CurrentChannel = u.parent.server.serverCh.get(ch);
		u.CurrentChannel.vecClients.add(u.parent);
		
		u.CurrentChannelNo = ch;
		
		Packet pAck = new Packet( OpcodeGame.EVENT_ENTER_CHANNEL_ACK );
		
		if( ch != -1 )
			pAck.writeInt(0); // OK
		else
			pAck.writeInt(1); // fail
				
		pAck.write(chtype); // ch type
		pAck.writeInt(0x5923DDF0); // open time
		pAck.writeInt(0x59252F6F); // close time
		
		u.parent.sendPacket(pAck, false);
	}
	
	public static void EVENT_LEAVE_CHANNEL_NOT(GameUser u) {
		u.LeaveCh();
	}
	
	public static boolean ChatCommand(GameUser u, String chat) {
		// 도움말
		if( chat.equals("/명령어") == true ) {
			u.SendChat("플지컴서버", "", "다음은 플지컴에서 사용 가능한 명령어 입니다.");
			u.SendChat("플지컴서버", "", "'!할말' = 외치기");
			u.SendChat("플지컴서버", "", "'@할말' = 확성기");
			u.SendChat("플지컴서버", "", "'/색 FF0000' = 채팅 색 빨강으로 변경");
			u.SendChat("플지컴서버", "", "'/색풀기' = 채팅 색 원래대로");
			u.SendChat("플지컴서버", "", "'/동접' = 현재 동시 접속자");
			u.SendChat("플지컴서버", "", "'/방정보 2' = 2번방의 정보 보기");
			return true;
		}
		
		// 외치기
		if( chat.indexOf("!") == 0 ) {
			int num = u.parent.server.clients.size();
			
			for( int i=0; i<num; i++ ) {
				GameClient c = (GameClient)u.parent.server.clients.get(i); 
				c.getGameUser().SendChat("[외치기]" + u.Nick, "", chat.substring(1));
			}
			
			return true;
		}
		
		// 확성기
		if( chat.indexOf("@") == 0 ) {
			long currentTime = Calendar.getInstance().getTimeInMillis();
			long t = (u.lastMegaphone + (1000 * 100)) - currentTime;
			if( t > 0 ) {
				u.SendChat("플지컴서버", "", "아직 확성기를 사용할 수 없습니다. 앞으로 " + (int)(t / 1000) + "초 후에 사용 가능합니다.");
			} else {
				u.lastMegaphone = currentTime;
				
				int num = u.parent.server.clients.size();
				
				for( int i=0; i<num; i++ ) {
					GameClient c = (GameClient)u.parent.server.clients.get(i); 
					c.getGameUser().SendSignBoard(u.Nick, "", "[확성기]" + u.Nick + ":" + chat.substring(1));
				}
			}
			
			return true;
		}
		
		// 채팅색
		if( chat.indexOf("/색 ") == 0 ) {
			String newcolor = chat.substring(3);
			
			if( newcolor.length() < 6 ) {
				u.SendChat("플지컴서버", "", "채팅 색 변경이 실패하였습니다. (색코드가 잘못됨)");
			} else {
				u.chatColor = newcolor.substring(0, 6);
				u.SendChat("플지컴서버", "", "채팅 색이 변경되었습니다. (색: #c" + u.chatColor + "■#cX)");
			}
			
			return true;
		}
		
		// 색풀기
		if( chat.indexOf("/색풀기") == 0 ) {
			u.chatColor = null;
			u.SendChat("플지컴서버", "", "채팅 색을 풀었습니다.");
			
			return true;
		}
		
		// 동접
		if( chat.indexOf("/동접") == 0 ) {
			int num = u.parent.server.clients.size();
			
			u.SendChat("플지컴서버", "", "현재 서버의 동접자: " + num + "명");
			
			for( int i=0; i<num; i++ ) {
				GameClient c = (GameClient)u.parent.server.clients.get(i);
				u.SendChat("플지컴서버", "", "-> " + c.getGameUser().Nick);
			}
			
			return true;
		}
		
		// 방정보
		if( chat.indexOf("/방정보 ") == 0 ) {
			String strroomno = chat.substring(5);
			
			if( strroomno.length() <= 0 ) {
				u.SendChat("플지컴서버", "", "방번호를 입력해주세요.");
				return true;
			}
			
			int roomno = -1;
			try { roomno = Integer.parseInt(strroomno); }
			catch(Exception e) {}
			
			roomno -= 1; // 방 번호는 실제로 1부터 시작하기 떄문에 1을 입력하면 0으로 처리해야한다
			
			if( roomno < 0 ) {
				u.SendChat("플지컴서버", "", "방번호가 잘못되었습니다.");
				return true;
			}
			
			Room r = u.CurrentChannel.mapRooms.get( (short)roomno );
			if( r == null ) {
				u.SendChat("플지컴서버", "", "존재하지 않는 방입니다.");
				return true;
			}
			
			u.SendChat("플지컴서버", "", "====================================");
			
			u.SendChat("플지컴서버", "", "방이름  :" + r.RoomName);
			u.SendChat("플지컴서버", "", "게임모드: " + r.GameMode);
			if( r.RandomMap )
				u.SendChat("플지컴서버", "", "맵      : " + r.GameMap);
			else
				u.SendChat("플지컴서버", "", "맵      : 랜덤");
			u.SendChat("플지컴서버", "", "시작상태: " + r.isPlaying);
			u.SendChat("플지컴서버", "", "참가인원: " + r.GetPlayerCount() + "명");
			
			for( int i=0; i<6; i++ ) {
				if( r.slots[i].isActive == false ) continue;
				
				String who = "";
				who += "자리" + i + "   : " + r.slots[i].client.getGameUser().Nick + " / ";
				switch( r.slots[i].state ) {
				case 0: who += "대기"; break;
				case 1: who += "레디"; break;
				case 3: who += "장비"; break;
				case 4: who += "미션"; break;
				case 6: who += "스킬트리"; break;
				}
				
				if( r.slots[i].isHost == true )
					who += " / *방장*";
				
				u.SendChat("플지컴서버", "", who);
			}
			
			u.SendChat("플지컴서버", "", "====================================");
			
			return true;
		}
		
		return false;
	}
	
	public static void EVENT_CHAT_REQ(GameUser u, Packet p) {
		// 외치기/일반/파티/귓/공지/무조건귓/색깔채팅/커플/영자/팀/팀색깔/길드
		byte m_cChatType = p.readByte();
		int m_dwSenderUID = p.readInt();
		String m_strSenderNick = p.readUnicodeStringWithLength();
		int m_dwReceiverUID = p.readInt();
		String m_strReceiverNick = p.readUnicodeStringWithLength();
		int m_dwChatColor = p.readInt();
		String m_strChatMsg = p.readUnicodeStringWithLength();
		int m_iParam = p.readInt();
		int m_iParamReserved = p.readInt();
		
		if( ChatCommand(u, m_strChatMsg) == true )
			return;
		
		// 채팅 색
		if( u.chatColor != null ) {
			if( m_cChatType == 1 ) m_cChatType = 8; // #c가 필터링 당하는걸 방지
			m_strChatMsg = "#c" + u.chatColor + m_strChatMsg + "#cX";
		}
		
		Packet pChat = new Packet(OpcodeGame.EVENT_CHAT_NOT);
		pChat.write(m_cChatType);
		pChat.writeInt(u.LoginUID);
		pChat.writeUnicodeStringWithLength(u.Nick);
		pChat.writeInt(m_dwReceiverUID);
		pChat.writeUnicodeStringWithLength(m_strReceiverNick);
		pChat.writeInt(m_dwChatColor);
		pChat.writeUnicodeStringWithLength(m_strChatMsg);
		pChat.writeInt(m_iParam);
		pChat.writeInt(m_iParamReserved);
		
		int onlineusernum = u.CurrentChannel.vecClients.size();
		for( int i=0; i<onlineusernum; i++ ) {
			GameClient target = u.CurrentChannel.vecClients.get(i);
			
			if( target != null )
				if( target.getGameUser().CurrentChannel == u.CurrentChannel )
					if( target.getGameUser().CurrentRoom == u.CurrentRoom )
						target.sendPacket(pChat, false);
		}
	}
	
	@SuppressWarnings("unused")
	public static void EVENT_ROOM_LIST_REQ(GameUser u, Packet p) {
		// 채널에 입장을 안했는데 방 목록 요청
		if( u.CurrentChannel == null )
			return;

		// 이미 방에 있는데 방 목록 요청
		if( u.CurrentRoom != null )
			return;
		
		boolean m_bWaitRoom = p.readBool();
		int m_nType = p.readInt();
		int m_nPage = p.readInt();
		int m_nDifficult = p.readInt();
		
		// 방 정보 조합하자		
		Set<Short> keySet;
		Iterator<Short> it; 
		
		// 방 개수부터 구한다
		int roomcount = 0;
		
		keySet = u.CurrentChannel.mapRooms.keySet();
		it = keySet.iterator();
		
		while( it.hasNext() ) {
			short roomid = it.next();
			Room r = u.CurrentChannel.mapRooms.get(roomid);
			
			if( m_bWaitRoom == true )
				if( r.isPlaying == true || r.GetFreeSlotCount() == 0 )
					continue;
			
			roomcount++; // 방 개수 늘려주고
		}
		
		// 비효율적.......
		Packet ptemp = new Packet(OpcodeGame.EVENT_ROOM_LIST_ACK);
		
		// 방 개수 담는다
		ptemp.writeInt(roomcount);
		
		keySet = u.CurrentChannel.mapRooms.keySet();
		it = keySet.iterator();
		
		while( it.hasNext() ) {
			short roomid = it.next();
			Room r = u.CurrentChannel.mapRooms.get(roomid);
			
			if( m_bWaitRoom == true )
				if( r.isPlaying == true || r.GetFreeSlotCount() == 0 )
					continue;
			
			ptemp.writeShort(r.RoomID);
			ptemp.writeUnicodeStringWithLength(r.RoomName);
			if( r.RoomPass.length() > 0 )
				ptemp.write(0);
			else
				ptemp.write(1);
			ptemp.write(0);
			ptemp.writeUnicodeStringWithLength(r.RoomPass);
			ptemp.writeShort( (short) (r.GetFreeSlotCount() + r.GetPlayerCount()) );
			ptemp.writeShort( (short) r.GetPlayerCount() );
			ptemp.writeBool( r.isPlaying );
			ptemp.writeHexString("2E 02 1B 25 01 00 00 00 00 01 6B F9 38 77 00 00 00 0C 00 00 00 00 00 00 00 01");
			if( r.GetRoomHost() == null )
				ptemp.writeInt(0);
			else
				ptemp.writeUnicodeStringWithLength( r.GetRoomHost().getGameUser().Nick );
			ptemp.writeHexString("0B 00 00 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 00 01");
		}
		
		ptemp.applyWrite();
		
		int originalSize = ptemp.buffer.length;
		byte[] roominfoBuffer = Crypto.zlibcompress(ptemp.buffer);
		ptemp = null;
		
		Packet pRoom = new Packet(OpcodeGame.EVENT_ROOM_LIST_ACK);
		pRoom.writeHexString("00 00 00 00 00 00 00 00 00 00 00 01");
		pRoom.writeInt(4 + roominfoBuffer.length);
		pRoom.write(1);
		pRoom.writeIntLE(originalSize);
		pRoom.write(roominfoBuffer);
		u.parent.sendPacket(pRoom, false);
	}
	
	@SuppressWarnings("unused")
	public static void EVENT_CREATE_ROOM_REQ(GameUser u, Packet p) {
		short RoomID = p.readShort();
		String RoomName = p.readUnicodeStringWithLength();
		boolean isPublic = p.readBool();
		boolean isGuild = p.readBool();
		String RoomPass = p.readUnicodeStringWithLength();
		short usUsers = p.readShort();
		short usMaxUsers = p.readShort();
		boolean isPlaying = p.readBool();
		byte cGrade = p.readByte();
		byte cGameCategory = p.readByte();
		int iGameMode = p.readInt();
		int iSubGameMode = p.readInt();
		boolean bRandomMap = p.readBool();
		int MapID = p.readInt();
		int P2PVersion = p.readInt();
		boolean slot1 = p.readBool();
		boolean slot2 = p.readBool();
		boolean slot3 = p.readBool();
		boolean slot4 = p.readBool();
		boolean slot5 = p.readBool();
		boolean slot6 = p.readBool();
		int MonsterID = p.readInt();
		int MonsterCount = p.readInt();
		int Difficulty = p.readInt();
		byte m_cRoutingMethod = p.readByte();
		int RSIP = p.readInt();
		short RSPort = p.readShort();
		int TRSIP = p.readInt();
		short TRSPort = p.readShort();
		boolean m_bEnableJoinGame = p.readBool();
		boolean m_bDeathMatchIntrudable = p.readBool();
		boolean m_bDeathMatchBalancing = p.readBool();
		int m_nDeathMatchTime = p.readInt();
		int m_nDeathKillCount = p.readInt();
		int m_dwUniqueNumber = p.readInt();
		long m_biStartingTotalExp = p.readLong();
		int m_dwStartingTotalGp = p.readInt();
		int m_nTotalLv = p.readInt();
		p.readUnicodeStringWithLength(); // m_pairGuildMarkName left
		p.readUnicodeStringWithLength(); // m_pairGuildMarkName right
		p.readUnicodeStringWithLength(); // m_pairGuildName left
		p.readUnicodeStringWithLength(); // m_pairGuildName right
		p.readInt(); // m_pairBattlePoint left
		p.readInt(); // m_pairBattlePoint right
		String CountryCode = p.readUnicodeStringWithLength();
		short m_usDefaultUser = p.readShort();
		boolean m_bModeChangeEnable = p.readBool();
		int m_nRewardType = p.readInt();
		
		// KInDoorUserInfo ▼
		String strLogin = p.readUnicodeStringWithLength();
		int UserUID = p.readInt();
		String strNick = p.readUnicodeStringWithLength();
		int PlayerSlotIndex = p.readInt();
		byte CharIndex = p.readByte(); // 선택된 캐릭터
		byte CharPromotion = p.readByte();
		byte TagMatchInfo1 = p.readByte();
		byte TagMatchInfo2 = p.readByte();
		byte TagMatchInfo3 = p.readByte();
		byte TagMatchInfo4 = p.readByte();
		byte TagMatchInfo5 = p.readByte();
		byte TagMatchInfo6 = p.readByte();
		int iTeam = p.readInt();
		boolean sex = p.readBool();
		int age = p.readInt();
		byte authlevel = p.readByte();
		boolean isObserver = p.readBool();
		int GP = p.readInt();
		boolean isPCBang = p.readBool();
		byte PCBangType = p.readByte();
		DungeonUserInfo.read_DungeonUserInfoPacketForDummy( p );
		boolean m_bIsHost = p.readBool();
		boolean m_bIsLive = p.readBool();
		int GuildUID = p.readInt();
		String MarkName = p.readUnicodeStringWithLength();
		String GuildName = p.readUnicodeStringWithLength();
		
		u.CurrentChar = CharIndex;
		u.CurrentPromotion = CharPromotion;

		// KInDoorUserInfo 속의 KInDoorCharInfo 시작
		/*
		int CharInfoSize = p.readInt();
		for( int i=0; i<CharInfoSize; i++ ) {
			byte CharType = p.readByte();
			String CharName = p.readUnicodeStringWithLength();
			byte cPromotion = p.readByte();
			byte cCurrentPromotion = p.readByte();
			long biExp = p.readLong();
			int dwLevel = p.readInt();
			int iWin = p.readInt();
			int iLose = p.readInt();
			
			int EquipItemSize = p.readInt();
			for( int jj=0; jj<EquipItemSize; jj++ ) {
				EquipItemInfo.read_EquipItemInfoPacketForDummy( p );
			}
			
			int EquipLookItemSize = p.readInt();
			for( int jj=0; jj<EquipItemSize; jj++ ) {
				EquipItemInfo.read_EquipItemInfoPacketForDummy( p );
			}
			
			PetInfo.read_PetInfoPacketForDummy(p);
			
			int mapSkillSetSize = p.readInt();
			for( int jj=0; jj<mapSkillSetSize; jj++ ) {
				p.readByte(); // 맵 인덱스
				
				int vecSkillSlotSize = p.readInt();
				for( int kkk=0; kkk<vecSkillSlotSize; kkk++ ) {
					p.readInt(); // 슬롯 인덱스
					p.readInt(); // 슬롯 ID
					p.readInt(); // 스킬 ID
				}
			}
			
			int SPPoint = p.readInt();
			int MaxSPPoint = p.readInt();
			EquipItemInfo.read_EquipItemInfoPacketForDummy( p ); // 무기체인지
			
			int vecSPInfoSize = p.readInt();
			for( int jj=0; jj<vecSPInfoSize; jj++ ) {
				p.readByte(); // m_cCharType
				p.readByte(); // m_cPromotion
				
				int vecSkillsSize = p.readInt();
				for( int kkk=0; kkk<vecSkillsSize; kkk++ ) {
					p.readInt(); // int m_vecSkills
				}
			}
			
			int vecSkillTreeInfo = p.readInt();
			for( int jj=0; jj<vecSkillTreeInfo; jj++ ) {
				p.readByte(); // m_cCharType
				p.readByte(); // m_cPromotion
				
				int vecSkillsSize = p.readInt();
				for( int kkk=0; kkk<vecSkillsSize; kkk++ ) {
					p.readInt(); // int m_vecSkills
				}
			}
			
			int TreeSPPoint = p.readInt();
			int MaxTreeSPPoint = p.readInt();
			
			int mapEquipSkillTree = p.readInt(); // m_mapEquipSkillTree
			for( int jj=0; jj<mapEquipSkillTree; jj++ ) {
				p.readByte(); // 맵 인덱스
				
				int vecSkillSlotSize = p.readInt();
				for( int kkk=0; kkk<vecSkillSlotSize; kkk++ ) {
					p.readInt(); // 슬롯 인덱스
					p.readInt(); // 슬롯 ID
					p.readInt(); // 스킬 ID
				}
			}
			
			int setPromotionSize = p.readInt(); // m_setPromotion
			for( int jj=0; jj<setPromotionSize; jj++ )
				p.readByte();
			
			ELOUserData.read_ELOUserDataPacketForDummy(p);
		}
		
		// m_vecIP
		int vecIPSize = p.readInt();
		for( int i=0; i<vecIPSize; i++ ) {
			p.readInt();
		}
		
		// m_vecPort
		int vecPortSize = p.readInt();
		for( int i=0; i<vecPortSize; i++ ) {
			p.readShort();
		}
		
		int nState = p.readInt();
		int PremiumInfo = p.readInt();
		*/
		
		// 걍 버리고 방 만들어준다.
		
		RoomID = u.CurrentChannel.getEmptyRoomID();
		
		// 방을 더 못 만든다. 원래 패킷으로 처리해야되는데 귀찮다
		if( RoomID == -1 )
			return;
		
		// 방을 만들고 맵에 넣는다
		Room room = new Room(u.CurrentChannel);
		u.CurrentChannel.mapRooms.put(RoomID, room);
		
		room.RoomID = RoomID;
		room.RoomName = RoomName;
		room.RoomPass = RoomPass;
		room.GameCategory = cGameCategory;
		room.GameMode = iGameMode;
		room.ItemMode = iSubGameMode;
		room.RandomMap = bRandomMap;
		room.GameMap = MapID;
		room.GameDifficulty = Difficulty;
		room.isPlaying = isPlaying;
		
		for(int i=0; i<6; i++)
			room.slots[i].init();
		
		// 내가 들어간다
		room.slots[0].isActive = true;
		room.slots[0].isOpen = false;
		room.slots[0].isHost = true;
		room.slots[0].client = u.parent;
		
		u.CurrentRoom = room;
		u.CurrentRoomNo = RoomID;
		
		Packet pOK = new Packet(OpcodeGame.EVENT_CREATE_ROOM_ACK);
		pOK.writeInt(0);
		pOK.writeUnicodeStringWithLength(u.ID);
		pOK.writeInt(u.LoginUID);
		pOK.writeUnicodeStringWithLength(u.Nick);
		pOK.writeInt(room.GetSlotIndex(u.parent));
		pOK.write( u.CurrentChar );
		pOK.write( u.CurrentPromotion );
		pOK.write(TagMatchInfo1);
		pOK.write(TagMatchInfo2);
		pOK.write(TagMatchInfo3);
		pOK.write(TagMatchInfo4);
		pOK.write(TagMatchInfo5);
		pOK.write(TagMatchInfo6);
		pOK.writeInt(iTeam);
		pOK.writeBool(sex);
		pOK.writeInt(age);
		pOK.write(u.AuthLevel);
		pOK.writeBool(false); // isObserver
		pOK.writeInt(u.GamePoint);
		pOK.writeBool(u.isPCBang);
		pOK.write(u.PCBangType);

		
		//pOK.writeHexString("00 00 00 4E 00 00 00 07 00 00 00 01 01 01 00 00 00 00 00 00 00 00 00 08 00 00 00 01 01 01 00 00 00 00 00 00 00 00 00 09 00 00 00 01 01 01 00 00 00 00 00 00 00 00 00 0A 00 00 00 01 01 01 00 00 00 00 00 00 00 00 00 0B 00 00 00 01 01 01 00 00 00 00 00 00 00 00 00 0C 00 00 00 01 01 01 00 00 00 00 00 00 00 00 00 0D 00 00 00 01 01 01 00 00 00 00 00 00 00 00 00 0E 00 00 00 01 01 01 00 00 00 00 00 00 00 00 00 0F 00 00 00 01 01 01 00 00 00 00 00 00 00 00 00 10 00 00 00 01 01 01 00 00 00 00 00 00 00 00 00 11 00 00 00 01 01 01 00 00 00 00 00 00 00 00 00 12 00 00 00 01 01 01 00 00 00 00 00 00 00 00 00 13 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 14 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 15 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 16 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 17 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 18 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 19 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 1A 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 1B 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 1D 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 1E 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 24 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 27 00 00 00 01 03 01 00 00 00 01 00 00 00 00 00 28 00 00 00 01 03 01 00 00 00 01 00 00 00 00 00 29 00 00 00 01 03 01 00 00 00 01 00 00 00 00 00 2A 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 2B 00 00 00 01 03 01 00 00 00 01 00 00 00 00 00 2C 00 00 00 01 03 01 00 00 00 01 00 00 00 00 00 2D 00 00 00 01 03 01 00 00 00 01 00 00 00 00 00 2E 00 00 00 01 03 01 00 00 00 01 00 00 00 00 00 2F 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 30 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 31 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 32 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 33 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 34 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 35 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 36 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 37 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 38 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 39 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 3A 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 3B 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 3C 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 3D 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 3E 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 3F 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 40 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 43 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 44 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 45 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 46 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 47 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 48 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 49 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 4A 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 4B 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 4C 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 4E 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 4F 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 50 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 51 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 52 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 53 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 54 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 55 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 56 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 57 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 58 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 59 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 5A 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 5B 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 5C 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 5D 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 5E 00 00 00 01 07 01 00 01 00 02 00 00 00 00 00 5F 00 00 00 00 00 00 00 00 00 00 00 01 01 00 00");
		DungeonUserInfo.write_mapDifficulty(pOK);
		
		//pOK.writeHexString("00 00 00 4E 00 00 00 07 00 00 00 00 00 00 00 00 00 00 00 00 00 00 08 00 00 00 00 00 00 00 00 00 00 00 00 00 00 09 00 00 00 00 00 00 00 00 00 00 00 00 00 00 0A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 0B 00 00 00 00 00 00 00 00 00 00 00 00 00 00 0C 00 00 00 00 00 00 00 00 00 00 00 00 00 00 0D 00 00 00 00 00 00 00 00 00 00 00 00 00 00 0E 00 00 00 00 00 00 00 00 00 00 00 00 00 00 0F 00 00 00 00 00 00 00 00 00 00 00 00 00 00 10 00 00 00 00 00 00 00 00 00 00 00 00 00 00 11 00 00 00 00 00 00 00 00 00 00 00 00 00 00 12 00 00 00 00 00 00 00 00 00 00 00 00 00 00 13 00 00 00 00 00 00 00 00 00 00 00 00 00 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 15 00 00 00 00 00 00 00 00 00 00 00 00 00 00 16 00 00 00 00 00 00 00 00 00 00 00 00 00 00 17 00 00 00 00 00 00 00 00 00 00 00 00 00 00 18 00 00 00 00 00 00 00 00 00 00 00 00 00 00 19 00 00 00 00 00 00 00 00 00 00 00 00 00 00 1A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 1B 00 00 00 00 00 00 00 00 00 00 00 00 00 00 1D 00 00 00 00 00 00 00 00 00 00 00 00 00 00 1E 00 00 00 00 00 00 00 00 00 00 00 00 00 00 24 00 00 00 00 00 00 00 00 00 00 00 00 00 00 27 00 00 00 00 00 00 00 00 00 00 00 00 00 00 28 00 00 00 00 00 00 00 00 00 00 00 00 00 00 29 00 00 00 00 00 00 00 00 00 00 00 00 00 00 2A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 2B 00 00 00 00 00 00 00 00 00 00 00 00 00 00 2C 00 00 00 00 00 00 00 00 00 00 00 00 00 00 2D 00 00 00 00 00 00 00 00 00 00 00 00 00 00 2E 00 00 00 00 00 00 00 00 00 00 00 00 00 00 2F 00 00 00 00 00 00 00 00 00 00 00 00 00 00 30 00 00 00 00 00 00 00 00 00 00 00 00 00 00 31 00 00 00 00 00 00 00 00 00 00 00 00 00 00 32 00 00 00 00 00 00 00 00 00 00 00 00 00 00 33 00 00 00 00 00 00 00 00 00 00 00 00 00 00 34 00 00 00 00 00 00 00 00 00 00 00 00 00 00 35 00 00 00 00 00 00 00 00 00 00 00 00 00 00 36 00 00 00 00 00 00 00 00 00 00 00 00 00 00 37 00 00 00 00 00 00 00 00 00 00 00 00 00 00 38 00 00 00 00 00 00 00 00 00 00 00 00 00 00 39 00 00 00 00 00 00 00 00 00 00 00 00 00 00 3A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 3B 00 00 00 00 00 00 00 00 00 00 00 00 00 00 3C 00 00 00 00 00 00 00 00 00 00 00 00 00 00 3D 00 00 00 00 00 00 00 00 00 00 00 00 00 00 3E 00 00 00 00 00 00 00 00 00 00 00 00 00 00 3F 00 00 00 00 00 00 00 00 00 00 00 00 00 00 40 00 00 00 00 00 00 00 00 00 00 00 00 00 00 43 00 00 00 00 00 00 00 00 00 00 00 00 00 00 44 00 00 00 00 00 00 00 00 00 00 00 00 00 00 45 00 00 00 00 00 00 00 00 00 00 00 00 00 00 46 00 00 00 00 00 00 00 00 00 00 00 00 00 00 47 00 00 00 00 00 00 00 00 00 00 00 00 00 00 48 00 00 00 00 00 00 00 00 00 00 00 00 00 00 49 00 00 00 00 00 00 00 00 00 00 00 00 00 00 4A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 4B 00 00 00 00 00 00 00 00 00 00 00 00 00 00 4C 00 00 00 00 00 00 00 00 00 00 00 00 00 00 4E 00 00 00 00 00 00 00 00 00 00 00 00 00 00 4F 00 00 00 00 00 00 00 00 00 00 00 00 00 00 50 00 00 00 00 00 00 00 00 00 00 00 00 00 00 51 00 00 00 00 00 00 00 00 00 00 00 00 00 00 52 00 00 00 00 00 00 00 00 00 00 00 00 00 00 53 00 00 00 00 00 00 00 00 00 00 00 00 00 00 54 00 00 00 00 00 00 00 00 00 00 00 00 00 00 55 00 00 00 00 00 00 00 00 00 00 00 00 00 00 56 00 00 00 00 00 00 00 00 00 00 00 00 00 00 57 00 00 00 00 00 00 00 00 00 00 00 00 00 00 58 00 00 00 00 00 00 00 00 00 00 00 00 00 00 59 00 00 00 00 00 00 00 00 00 00 00 00 00 00 5A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 5B 00 00 00 00 00 00 00 00 00 00 00 00 00 00 5C 00 00 00 00 00 00 00 00 00 00 00 00 00 00 5D 00 00 00 00 00 00 00 00 00 00 00 00 00 00 5E 00 00 00 00 00 00 00 00 00 00 00 00 00 00 5F 00 00 00 00 00 00 00 00 00 00 00");
		pOK.writeBool(true); // 방만드니까 당연히 호스트
		pOK.writeBool(true); // 방만드니까 당연히 라이브
		pOK.writeInt( u.Guild.m_dwUID );
		pOK.writeUnicodeStringWithLength(u.Guild.m_strFileName );
		pOK.writeUnicodeStringWithLength(u.Guild.m_strName );
        
		int charcount = u.Characters.size(); 
		pOK.writeInt(charcount); // 캐릭터..
		for( int i=0; i<charcount; i++) {
			// KInDoorCharInfo 
			u.Characters.get(i).write_KInDoorCharInfo(u, pOK);
		}
		
		// IP
		pOK.writeInt(3); // size
		pOK.writeInt(0);
		pOK.writeInt(0);
		pOK.writeIntLE( Convert.IPToInt(u.parent.s.getInetAddress().getHostAddress()) );
		pOK.writeInt(1); // size
		pOK.writeShort( (short) 0x7EFE ); // port
		
		// 하드코딩
		pOK.writeInt(0); // state
		pOK.writeInt(u.PremiumType); // 프리미엄
		pOK.writeHexString("00 00 00 02 00 00 00 00 00 00 E5 6A 00 00 00 01 31 7F 24 36 00 00 00 00 01 00 00 E5 88 00 00 00 01 31 7F 24 37 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 01 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 01 00 00 00 00 00 00 00 02 00 00 00 00 00 00 00 00 01 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00");
		
		// 방정보 KRoomInfo
		Room.write_KRoomInfo(pOK, room);
		
		u.parent.sendPacket(pOK, true);
	}

	public static void EVENT_WHISPER_REQ(GameUser u, Packet p) {
		/*
	    kRecv.m_nOK
	    0 : 성공
	    1 : 귓속말 전송주기가 짧다.
	    2 : 귓속말 대상 유저 이름이 비었거나 메세지가 없음.
	    3 : 방송용 서버에서 운영자에게 귓속말을 할 수 없음.
	    4 : 대상 유저를 찾을 수 없음.
	    5 : 채팅 블럭 유저임.
	    */
		
		String who = p.readUnicodeStringWithLength();
		String msg = p.readUnicodeStringWithLength();
		
		GameClient target = null;
		
		int num = u.parent.server.clients.size();
		for( int i=0; i<num; i++ ) {
			GameClient c = (GameClient)u.parent.server.clients.get(i);
			if( c.getGameUser().Nick.equals(who) ) {
				target = c;
				break;
			}
		}
		
		Packet pr = new Packet(OpcodeGame.EVENT_WHISPER_ACK);
		
		// 귓말 상대가 있다면...
		if( target != null ) {
			Packet pChat = new Packet(OpcodeGame.EVENT_CHAT_NOT);
			pChat.write(3); // 귓말
			pChat.writeInt(u.LoginUID);
			pChat.writeUnicodeStringWithLength(u.Nick);
			pChat.writeInt(-1);
			pChat.writeUnicodeStringWithLength("");
			pChat.writeInt(0xFFFFFFFF);
			pChat.writeUnicodeStringWithLength(msg);
			pChat.writeInt(-1);
			pChat.writeInt(-1);
			target.sendPacket(pChat, false); // 귓말 상대에게 귓을 보내고...
			
			// 귓말 성공
			pr.writeInt(0);
			
		} else {
			// 귓말 상대가 없다..
			pr.writeInt(4);
		}
		
		pr.write(3); // 귓말
		pr.writeInt(u.LoginUID);
		pr.writeUnicodeStringWithLength(u.Nick);
		pr.writeInt(-1);
		pr.writeUnicodeStringWithLength("");
		pr.writeInt(0xFFFFFFFF);
		pr.writeUnicodeStringWithLength(msg);
		pr.writeInt(-1);
		pr.writeInt(-1);
		u.parent.sendPacket(pr, false); // 귓말 성공했다...
	}
}
