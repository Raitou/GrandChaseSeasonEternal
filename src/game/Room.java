package game;

import client.GameClient;
import game.etcclass.KInDoorUserInfo;
import game.user.DungeonUserInfo;
import game.user.GameUser;
import packet.OpcodeGame;
import packet.Packet;
import server.Main;
import util.Convert;

public class Room {
	public Channel parentCh;
	public short RoomID;
	public String RoomName;
	public String RoomPass;
	public int RoomUnique;
	public int GameCategory;
	public int GameMode;
	public int ItemMode;
	public int GameMap;
	public int GameDifficulty;
	public boolean RandomMap;
	public boolean isPlaying;
	public RoomSlot[] slots;
	
	public int m_nDeathLimitTime;
	public int m_nDeathKillCount;
	public boolean m_bDeathMatchIntrudable;
	public boolean m_bDeathMatchBalancing;
	
	public int leftbancount; // 강퇴회수
	
	public Room(Channel ch) {
		slots = new RoomSlot[6];
		for( int i=0; i<6; i++ )
			slots[i] = new RoomSlot();
		
		parentCh = ch;
		RoomID = -1;
		RoomName = "";
		RoomPass = "";
		RoomUnique = (int)(System.currentTimeMillis() / 1000L);
		GameCategory = 0;
		GameMode = 1;
		ItemMode = 0;
		GameMap = 99;
		GameDifficulty = 0;
		RandomMap = false;
		isPlaying = false;
		
		m_nDeathLimitTime = 300;
		m_nDeathKillCount = 20;
		m_bDeathMatchIntrudable = true;
		m_bDeathMatchBalancing = true;
		
		leftbancount = 3;
	}
	
	public int GetPlayerCount() {
		int result = 0;
		for( RoomSlot s : slots ) {
			if( s.isActive == true )
				result++;
		}
		return result;
	}
	
	public int GetFreeSlotCount() {
		int result = 0;
		for( RoomSlot s : slots ) {
			if( s.isOpen == true )
				result++;
		}
		return result;
	}
	
	public int GetSlotIndex(GameClient c) {
		for( int i=0; i<6; i++ ) {
			if( slots[i].client == c )
				return i;
		}
		return -1;
	}
	
	public GameClient GetRoomHost() {
		for( RoomSlot s : slots ) {
			if( s.isHost == true )
				return s.client;
		}
		return null;
	}
	
	public void LeaveRoom(GameUser u) {
		LeaveRoom(u, 0); // 일반 퇴장
	}
	
	public void LeaveRoom(GameUser u, int why) {
		/*
		LEAVE_SELF_DECISION             = 0,
        LEAVE_BANNED_ON_PLAYING         = 1,
        LEAVE_BANNED_EXCEED_MAXUSER     = 2,
        LEAVE_BANNED_HOST_DECISION      = 3,
        LEAVE_SERVER_FIND_ERR           = 4,
        LEAVE_CLIENT_FIND_ERR           = 5,
        LEAVE_HOST_MIGRATION_FAILED     = 6,
        LEAVE_LOADING_COMPLETE_FAILED   = 7,
        LEAVE_MOVE_AGIT                 = 8,
		*/
		u.CurrentRoom = null;
		u.CurrentRoomNo = -1;
		
		// 슬롯부터 찾자
		int slot = GetSlotIndex(u.parent);
		if( slot == -1 )
			return;

		// 방장
		boolean wasHost = false;
		wasHost = slots[slot].isHost;

		// 다른 애들한테 나갔다고 알려준다
		Packet packetLeave = new Packet(OpcodeGame.EVENT_INFORM_USER_LEAVE_ROOM_NOT);
		packetLeave.writeUnicodeStringWithLength(u.ID);
		packetLeave.writeInt(why);
		packetLeave.writeInt(u.LoginUID);
		packetLeave.writeInt(leftbancount);
		
		for( int i=0; i<6; i++ )
			if( slots[i].isActive == true )
				slots[i].client.sendPacket(packetLeave, true, true);
		
		// 방에서 나간다
		slots[slot].init();
		
		// 나가는놈이 방장이고 방에 아직 인원이 남았다면...
		if( wasHost == true && GetPlayerCount() != 0 ) {
			// 방장 인계
			int npos = slot;
			for( int i=0; i<6; i++ ) {
				npos++;
				if( npos >= 6 ) npos = 0;
				if( slots[npos].isActive == true ) {
					slots[npos].isHost = true;
					slots[npos].state = 0;
					break;
				}
			}
			
			// 인계 알림
			Packet packetHost = new Packet(OpcodeGame.EVENT_HOST_MIGRATED_NOT);
			packetHost.writeInt( slots[npos].client.getGameUser().LoginUID );
			packetHost.write(0);
			
			for( int i=0; i<6; i++ )
				if( slots[i].isActive )
					slots[i].client.sendPacket(packetHost, false);
		}
		
		// 방에 나간 다음 방에 사람이 없으면 방을 파괴
		if( GetPlayerCount() <= 0 ) {
			parentCh.mapRooms.remove(RoomID);
		}
	}
	
	public static void Send_EVENT_JOIN_ROOM_INFO_DIVIDE_ACK(GameUser u) {
		Room room = u.CurrentRoom;
		if( room == null )
			return;
		
		int num = -1;
		
		for( int i=0; i<6; i++ ) {
			if( room.slots[i].isActive == false ) continue;
			
			num++;
			
			Packet p = new Packet(OpcodeGame.EVENT_JOIN_ROOM_INFO_DIVIDE_ACK);
			p.writeInt(0);
			p.writeInt(room.GetPlayerCount());
			p.writeInt(num);
			KInDoorUserInfo.write_KInDoorUserInfo( room.slots[i].client.getGameUser(), p );
			
			u.parent.sendPacket(p, false);
		}
	}
	
	public static void EVENT_LEAVE_ROOM_REQ(GameUser u, Packet p) {
		Room room = u.CurrentRoom;
		
		// 방에 안 들어가있는데 나가려고 시도...
		if( room == null )
			return;
		
		room.LeaveRoom( u );
		
		Packet pok = new Packet(OpcodeGame.EVENT_LEAVE_ROOM_ACK);
		pok.writeInt(0);
		u.parent.sendPacket(pok, false);
	}
	
	public static void EVENT_LEAVE_GAME_REQ(GameUser u, Packet p) {
		Room room = u.CurrentRoom;
		
		// 방에 안 들어가있는데 나가려고 시도...
		if( room == null )
			return;
		
		// 일단 방은 나가준다..
		room.LeaveRoom( u );
		
		// 게임중이 아닌데 나가려고 시도...
		if( room.isPlaying == false )
			return;
		
		Packet pok = new Packet(OpcodeGame.EVENT_LEAVE_GAME_ACK);
		pok.writeInt(0);
		u.parent.sendPacket(pok, false);
	}
	
	public static void EVENT_CHANGE_ROOM_INFO_REQ(GameUser u, Packet p) {
		Room room = u.CurrentRoom;
		
		if( room == null )
			return;
		
		// 요청자 슬롯 번호
		int slot = room.GetSlotIndex(u.parent);
		
		// 방장인가?
		boolean isHost = false;
		isHost = room.slots[slot].isHost;
		
		// 방장/운영자가 아닌데 방 바꾸려고 시도함
		if( isHost == false && u.AuthLevel != 3 ) {
			Main.printmsg("방장이 아닌데 방 정보를 바꾸려고 시도함\n" +
					      "아이디: " + u.ID +
					      "닉네임: " + u.Nick +
					      "슬롯: " + slot, 1);
			return;
		}
		
		int flag = p.readInt();
		p.readByte();
		p.readByte();
		p.readByte();
		byte nGameCategory = p.readByte();
		int nGameMode = p.readInt();
		int nItemMode = p.readInt();
		boolean nRandomMap = p.readBool();
		int nMap = p.readInt();
		int Difficulty = p.readInt();
		int MonsterID = p.readInt();
		int MonsterLevel = p.readInt();
		int MonsterCount = p.readInt();
		p.readInt();
		p.readInt();
		p.readInt();
		
		if( flag == 0 ) {
			room.GameCategory = nGameCategory;
			room.GameMode = nGameMode;
			room.ItemMode = nItemMode;
			room.RandomMap = nRandomMap;
			room.GameMap = nMap;
			room.GameDifficulty = Difficulty;
		}
		
		byte ChangeSlotNum = p.readByte();
		byte ChangeSlotInfo[] = new byte[ ChangeSlotNum * 2 ];
		if( ChangeSlotNum > 0 ) {
			int idx = 0;
			for( int i=0; i<ChangeSlotNum; i++ ) {
				byte target = ChangeSlotInfo[idx++] = p.readByte();
				byte open = ChangeSlotInfo[idx++] = p.readByte();
				room.slots[target].isOpen = (open == 1 ? true : false);
			}
		}
		
		Packet pBroad = new Packet(OpcodeGame.EVENT_CHANGE_ROOM_INFO_BROAD);
		pBroad.writeInt(0);
		pBroad.write(0);
		pBroad.write(0);
		pBroad.write(0);
		pBroad.write(room.GameCategory);
		pBroad.writeInt(room.GameMode);
		pBroad.writeInt(room.ItemMode);
		pBroad.writeBool(room.RandomMap);
		pBroad.writeInt(room.GameMap);
		pBroad.writeInt(room.GameDifficulty);
		pBroad.writeInt(-1);
		pBroad.writeInt(0);
		pBroad.write(0);
		pBroad.write(0);
		pBroad.write(0);
		pBroad.writeShort( room.GetPlayerCount() );
		pBroad.writeShort( room.GetFreeSlotCount() );
		for( int i = 0; i < 6; i++ )
			pBroad.writeBool(room.slots[i].isOpen);
		pBroad.writeInt(ChangeSlotNum);
		if( ChangeSlotNum > 0)
			pBroad.write(ChangeSlotInfo);
		if( ChangeSlotNum == 1)
			pBroad.writeHexString("00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00");
		else if( ChangeSlotNum == 1)
			pBroad.writeHexString("00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00");
		else
			pBroad.writeHexString("00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01");
		
		for( int i = 0; i < 6; i++ )
			if( room.slots[i].isActive == true )
				room.slots[i].client.sendPacket(pBroad, false);
	}
	
	public static void EVENT_JOIN_ROOM_REQ(GameUser u, Packet p) {
		Channel ch = u.CurrentChannel;
		Room room = u.CurrentRoom;
		
		if( ch == null ) { Send_CANT_JOIN_ROOM(u); return; }
		if( room != null ) { Send_CANT_JOIN_ROOM(u); return; }
		
		int reqJoinType = p.readInt();
		short reqRoomid = p.readShort();
		String reqRoompass = p.readUnicodeStringWithLength();
		byte m_cQuickJoinCategory = p.readByte();
		int m_nQuickJoinModeID = p.readInt();
		
		// 그다음은 KInDoorUserInfo 객체를 들어야하는데 나중에 구현하자
		String strLogin = p.readUnicodeStringWithLength();
		int dwUserUID = p.readInt();
		String strNickName = p.readUnicodeStringWithLength();
		int PlayerIndex = p.readInt();
		byte cCharIndex = p.readByte();
		byte cPromotion = p.readByte();
		// ...
		// ==========
		
		// 캐릭터 변경해주자
		u.CurrentChar = cCharIndex;
		u.CurrentPromotion = cPromotion;
		
		room = ch.mapRooms.get(reqRoomid);
		
		if( room == null ) { Send_CANT_JOIN_ROOM(u); return; }
		if( room.GetFreeSlotCount() == 0 ) { Send_CANT_JOIN_ROOM(u); return; }
		if( room.isPlaying == true ) { Send_CANT_JOIN_ROOM(u); return; }
		if( room.RoomPass.equals(reqRoompass) == false ) { Send_CANT_JOIN_ROOM(u); return; }
		
		// 적절한 슬롯 위치 구하기
		int pos = -1;
		
		if( u.CurrentChannelNo == Channel.CH_NUM_PVP ) {
			// 대전이면 팀을 잘 맞춰서...
			byte team1 = 0, team2 = 0, pos1 = -1, pos2 = -1;
			for( int i=0; i<3; i++ ) { // 세르딘
				if( room.slots[i].isActive ) team1++;
				if( pos1 == -1 && room.slots[i].isOpen == true ) pos1 = (byte) i;
			}
			for( int i=3; i<6; i++ ) { // 카나반
				if( room.slots[i].isActive ) team2++;
				if( pos2 == -1 && room.slots[i].isOpen == true ) pos2 = (byte) i;
			}
			
			// 일단 세르딘에 넣고, 세르딘이 더 많으면 카나반으로
			pos = pos1;
			if( team1 > team2 ) pos = pos2;
		} else {
			// 대전이 아닌경우 팀 대충 맞춘다..
			int pos1 = -1;
			for( int i=0; i<6; i++ ) {
				if( pos1 == -1 && room.slots[i].isOpen == true ) pos1 = (byte) i;
			}
			pos = pos1;
		}
		
		// 방 정보 입력 해주고
		room.slots[pos].afk = 0;
		room.slots[pos].client = u.parent;
		room.slots[pos].isActive = true;
		room.slots[pos].isHost = false;
		room.slots[pos].isOpen = false;
		room.slots[pos].LoadState = 0;
		room.slots[pos].state = 0;
		room.slots[pos].ping = 0;
		
		u.CurrentRoom = room;
		u.CurrentRoomNo = reqRoomid;
		
		// 방에 들어간 애들한테 들어온 유저 정보를 뿌린다.
		/*
		Packet pBroad = new Packet(OpcodeGame.EVENT_JOIN_ROOM_BROAD);
		KInDoorUserInfo.write_KInDoorUserInfo(u, pBroad);
		
		for( int i=0; i<6; i++ ) 
			if( room.slots[i].isActive == true && room.slots[i].client != u.parent ) 
				room.slots[i].client.sendPacket(pBroad, false);
		*/

		// 방에 들어갔다고 뿌려준다
		Packet pOK = new Packet(OpcodeGame.EVENT_JOIN_ROOM_INFO_ACK);
		Room.write_KRoomInfo(pOK, room);
		
		u.parent.sendPacket(pOK, false);
		
		//Send_EVENT_JOIN_ROOM_INFO_DIVIDE_ACK( u );
		
		// 첫번째 유저 정보만 뿌려준다
		Packet pFirst = new Packet(OpcodeGame.EVENT_JOIN_ROOM_INFO_DIVIDE_ACK);
		pFirst.writeInt(0);
		pFirst.writeInt(room.GetPlayerCount());
		pFirst.writeInt(0);
		
		int firstslot = -1;
		for( int i=0; i<6; i++ )
			if( room.slots[i].isActive == true ) {
				firstslot = i;
				break;
			}
				
		KInDoorUserInfo.write_KInDoorUserInfo( room.slots[firstslot].client.getGameUser(), pFirst );

		u.parent.sendPacket(pFirst, true);
	}
	
	public static void Send_CANT_JOIN_ROOM(GameUser u) {
		Packet p = new Packet(OpcodeGame.EVENT_JOIN_ROOM_INFO_DIVIDE_ACK);
		p.writeInt(6);
		p.writeHexString("00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 01 00 00 00 01 30 00 00 00 F9 00 00 09 0D 00 00 00 00 00 00 00 00 F2 04 00 00 00 00 00 00 13 49 F4 FC 09 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 09 13 F2 04 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00");
		u.parent.sendPacket(p, true);
	}
	
	public static void EVENT_CHANGE_ROOMUSER_INFO_REQ(GameUser u, Packet p) {
		Channel ch = u.CurrentChannel;
		Room room = u.CurrentRoom;
		
		if( ch == null || room == null )
			return;
		
		int nOK = p.readInt();
		int LoginUID = p.readInt();
		byte ChangeType = p.readByte();
		String ID = p.readUnicodeStringWithLength();
		int iTeam = p.readInt();
		int nUserSlot = p.readInt();
		int CharIndex = p.readByte();
		byte TagCharInfo1 = p.readByte();
		byte TagCharInfo2 = p.readByte();
		byte TagCharInfo3 = p.readByte();
		byte TagCharInfo4 = p.readByte();
		byte TagCharInfo5 = p.readByte();
		byte TagCharInfo6 = p.readByte();
		int State = p.readInt();
		boolean bGuild = p.readBool();
		
		// 패킷이랑 유저객체랑 아이디가 다름
		if( ID.equals(u.ID) == false )
			return;
		
		int slot = room.GetSlotIndex(u.parent);

		switch( ChangeType ) {
		case 0: // 캐릭터 변경
			u.CurrentChar = CharIndex;
			break;
		case 1: // 팀 변경
		case 2: // 유저 슬롯 변경
			int newslot = -1;
			
			// 정말로 팀을 바꿀때만...
			if( slot / 3 != iTeam ) {
				if( iTeam == 0 ) // 세르딘을 가고싶다
					for( int i=0; i<3; i++ ) 
						if( room.slots[i].isOpen == true ) {
							newslot = i;
							break;
						}
				if( iTeam == 1 ) // 카나반을 가고싶다
					for( int i=3; i<6; i++ ) 
						if( room.slots[i].isOpen == true ) {
							newslot = i;
							break;
						}
				
				if( newslot != -1 ) { // 자리 찾았다
					room.slots[newslot].afk = 0;
					room.slots[newslot].client = u.parent;
					room.slots[newslot].isActive = true;
					room.slots[newslot].isHost = room.slots[slot].isHost;
					room.slots[newslot].isOpen = false;
					room.slots[newslot].LoadState = 0;
					room.slots[newslot].state = 0;
					room.slots[newslot].ping = 0;
					room.slots[slot].init();
					
					slot = newslot;
					iTeam = newslot / 3;
				}
			}
			break;
		case 3: // 용사의섬 변경
			break;
		case 4: // 상태 변경
			room.slots[slot].state = State;
			break;
		}
		
		// 팀 변경 문제가 있다... 여기서 하드코딩해서 다뤄보자 ㅜㅜ
		switch( room.GameCategory ) {
		case GCEnum.GC_GMC_MATCH:
		case GCEnum.GC_GMC_GUILD_BATTLE:
		case GCEnum.GC_GMC_INDIGO:
		case GCEnum.GC_GMC_TAG_MATCH:
		case GCEnum.GC_GMC_DEATHMATCH:
		case GCEnum.GC_GMC_ANGELS_EGG:
		case GCEnum.GC_GMC_CAPTAIN:
		case GCEnum.GC_GMC_DOTA:
		case GCEnum.GC_GMC_FATAL_DEATHMATCH:
			iTeam = slot / 3;
			break;
		case GCEnum.GC_GMC_DUNGEON:
			iTeam = 0;
			break;
		}
		
		Packet pok = new Packet(OpcodeGame.EVENT_CHANGE_ROOMUSER_INFO_BROAD);
		pok.writeInt(0);
		pok.writeInt(u.LoginUID);
		pok.write(ChangeType);
		pok.writeUnicodeStringWithLength(u.ID);
		pok.writeInt(iTeam);
		pok.writeInt(slot);
		pok.write(u.CurrentChar);
		pok.write(-1); // 태그매치
		pok.write(0);
		pok.write(-1);
		pok.write(0);
		pok.write(-1);
		pok.write(0); // 태그매치 끝
		pok.writeInt(room.slots[slot].state);
		pok.writeBool(bGuild);
		
		for(int i=0; i<6; i++) 
			if( room.slots[i].isActive == true )
				room.slots[i].client.sendPacket(pok, false);
	}

	public static void EVENT_START_GAME_REQ(GameUser u, Packet p) {
		int UID = p.readInt();
		short RoomID = p.readShort();
		
		Room r = u.CurrentRoom;
		
		// 방에 안 들어가있는데?
		if( r == null )
			return;
		
		// 방장이 아닌데?
		if( r.GetRoomHost() != u.parent ) {
			Main.printmsg("방장이 아닌데 게임을 시작하려고 함\n"
					    + "방번호: " + r.RoomID +"\n"
					    + "닉네임: " + u.Nick, 1);
			return;
		}
		
		int needReady = r.GetPlayerCount() - 1; // 방장을 제외한 나머지
		int countReady = 0;
		for(int i=0; i<6; i++) 
			if( r.slots[i].isActive == true )
				if( r.slots[i].state == 1 )
					countReady++;
		
		// 레디 안되있는 사람도 있는데?
		if( needReady > countReady ) {
			Main.printmsg("준비된 사람이 부족한데 시작하려고 함\n"
					+ "방번호: " + r.RoomID +"\n"
				    + "준비된 사람 수: " + countReady, 1);
			return;
		}
		
		r.isPlaying = true;
		
		Packet sp = new Packet(OpcodeGame.EVENT_START_GAME_BROAD);
		
		sp.writeInt( 0 ); // OK
		sp.writeInt( (int)(System.currentTimeMillis() / 1000L) ); // 랜덤시드
		sp.writeInt( 0 ); // std::map< DWORD, std::set<int> > m_mapEvents; //< EventID, Start EffectID >
		sp.writeInt( 0 ); // std::vector<KChampionInfo> m_vecChampions;
		sp.writeInt( 0 ); // m_dropData vector
		sp.writeInt( 0 ); // m_dropData vector
		sp.writeInt( 0 ); // m_dropData vector
		sp.writeInt( r.GetPlayerCount() ); // m_vecStartingUsers
		for( int i=0; i<6; i++ )
			if( r.slots[i].isActive == true )
				sp.writeInt( r.slots[i].client.getGameUser().LoginUID );
		
		sp.writeInt( (int)(System.currentTimeMillis() / 1000L) ); // m_dwUniqueNumber 아마 릴레이에서 쓸거같음 걍 랜섬시드랑 같게;
		sp.writeInt( r.GetPlayerCount() ); // m_mapUserInvenInfo
		for( int i=0; i<6; i++ )
			if( r.slots[i].isActive == true ) {
				sp.writeInt( r.slots[i].client.getGameUser().LoginUID ); // uid
				sp.writeInt( 9999 ); // InvenTotalSize
				sp.writeInt( 1 ); // ItemCount
			}
		sp.writeInt(0); // std::vector<KEventMonster> m_vecEventMonster; // 이벤트 몬스터 정보
		sp.writeInt( r.GetRoomHost().getGameUser().LoginUID );
		
		// KChangeRoomInfo
		sp.writeInt(0); // ok
		sp.writeShort( r.RoomID );
		sp.writeBool( false ); // 길드
		sp.write( r.GameCategory );
		sp.writeInt( r.GameMode );
		sp.writeInt( r.ItemMode );
		sp.writeBool( r.RandomMap );
		sp.writeInt( r.GameMap );
		sp.writeInt( r.GameDifficulty );
		sp.writeInt( -1 ); // 몬스터ID
		sp.writeInt( 1 ); // 몬스터레벨
		sp.writeInt( 0 ); // 몬스터 수
		sp.write( r.GetPlayerCount() ); // m_cRoutingMethod
		sp.writeShort( r.GetFreeSlotCount() ); // Max User
		for( int i=0; i<6; i++ )
			if( r.slots[i].isActive == true )
				sp.writeBool( r.slots[i].isOpen ); // m_abSlotOpen
		sp.writeInt(0); // m_vecChangeSlot
		sp.writeInt(0); // m_pairGuildMarkName left
		sp.writeInt(0); // m_pairGuildMarkName right
		sp.writeInt(0); // m_pairGuildName left 
		sp.writeInt(0); // m_pairGuildName right
		sp.writeInt(0); // m_pairBattlePoint left 
		sp.writeInt(0); // m_pairBattlePoint right
		sp.writeShort(0); // KDefaultModeInfo 기본 최대 방원수.
		sp.writeBool(true); // KDefaultModeInfo  모드 변경 가능 여부.
		sp.writeInt(0); // KDefaultModeInfo 결과 보상 Type
		// =================================
		
		sp.writeInt(0); // std::map<KDropItemInfo, int> m_mapDotaItemInfo; // Dota 게임내 아이템 정보.
		sp.writeInt(0); // dota modeid
		sp.writeInt(0); // dota std::map< int, std::map<int,int> > m_mapModeMapInfo;   // < MapID, Core MonID >
		
		//sp.writeInt(0); // m_mapUserDPointInfo
		sp.writeInt( r.GetPlayerCount() ); // m_mapUserDPointInfo
		for( int i=0; i<6; i++ )
			if( r.slots[i].isActive == true ) {
				sp.writeInt( r.slots[i].client.getGameUser().LoginUID );
				sp.write( r.slots[i].client.getGameUser().CurrentChar );
				sp.writeInt( 1000 );
			}
		
		sp.writeBool(false); // 해당 던전에 호위 이벤트가 진행중일 경우 true
		sp.writeInt(0); // 등장할 호위 이벤트 몬스터ID,
		sp.writeInt(0); // 스페셜몬스터 id
		sp.writeInt(0); // 스페셜몬스터 레벨
		sp.writeInt(0); // 스페셜몬스터 프로퍼티
		
		// 방 유저에게 전송
		for( int i=0; i<6; i++ )
			if( r.slots[i].isActive == true )
				r.slots[i].client.sendPacket(sp, true);
	}

	public static void EVENT_RELAY_LOADING_STATE(GameUser u, Packet p) {
		int UID = p.readInt();
		int LoadState = p.readInt();
		
		Room r = u.CurrentRoom;
		
		if( r == null )
			return;
		
		int myslot = r.GetSlotIndex(u.parent);
		r.slots[myslot].LoadState = LoadState;
		
		Packet pr = new Packet(OpcodeGame.EVENT_RELAY_LOADING_STATE);
		pr.writeInt(UID);
		pr.writeInt(LoadState);
		
		// 방 유저에게 전송
		for( int i=0; i<6; i++ )
			if( r.slots[i].isActive == true )
				r.slots[i].client.sendPacket(pr, false);
	}

	public static void EVENT_LOAD_COMPLETE_NOT(GameUser u, Packet p) {
		// 누군가 로딩이 완료되었다고 보냈으니, 현재 방 인원이 다 로딩 됐는지 본다
		
		Room r = u.CurrentRoom;
		
		if( r == null )
			return;
		
		// 로딩 끝났다고 하는거니까 로딩상태를 17로 하드코딩.
		int myslot = r.GetSlotIndex(u.parent);
		r.slots[myslot].LoadState = 17;
		
		for( int i=0; i<6; i++ )
			if( r.slots[i].isActive == true )
				if( r.slots[i].LoadState < 17 )
					return;
		
		Packet pr = new Packet(OpcodeGame.EVENT_LOAD_COMPLETE_BROAD);
		pr.writeInt(0);
		
		// 방 유저에게 전송
		for( int i=0; i<6; i++ )
			if( r.slots[i].isActive == true )
				r.slots[i].client.sendPacket(pr, false);
	}

	public static void EVENT_STAGE_LOAD_COMPLETE_NOT(GameUser u, Packet p) {
		// 누군가 로딩이 완료되었다고 보냈으니, 현재 방 인원이 다 로딩 됐는지 본다

		Room r = u.CurrentRoom;
		
		if( r == null )
			return;
		
		for( int i=0; i<6; i++ )
			if( r.slots[i].isActive == true )
				if( r.slots[i].LoadState < 17 )
					return;
		
		Packet pr = new Packet(OpcodeGame.EVENT_STAGE_LOAD_COMPLETE_BROAD);
		pr.writeInt(0);
		
		// 방 유저에게 전송
		for( int i=0; i<6; i++ )
			if( r.slots[i].isActive == true )
				r.slots[i].client.sendPacket(pr, false);
	}

	public static void EVENT_ROOM_MEMBER_PING_INFO_REQ(GameUser u, Packet p) {
		Packet pr = new Packet(OpcodeGame.EVENT_ROOM_MEMBER_PING_INFO_ACK);
		
		Room r = u.CurrentRoom;
		
		if( r == null )
			return;
		
		pr.writeInt(r.GetPlayerCount());
		
		for( int i=0; i<6; i++ )
			if( r.slots[i].isActive == true ) {
				pr.writeInt(r.slots[i].client.getGameUser().LoginUID);
				pr.writeInt(r.slots[i].ping);
			}
		
		for( int i=0; i<6; i++ )
			if( r.slots[i].isActive == true )
				r.slots[i].client.sendPacket(pr, false);
	}

	public static void EVENT_GET_ROOMUSER_IDLE_STATE_REQ(GameUser u, Packet p) {
		Packet pr = new Packet(OpcodeGame.EVENT_GET_ROOMUSER_IDLE_STATE_ACK);
		
		Room r = u.CurrentRoom;
		
		if( r == null )
			return;
		
		pr.writeInt(r.GetPlayerCount());
		
		for( int i=0; i<6; i++ )
			if( r.slots[i].isActive == true ) {
				pr.writeInt(r.slots[i].client.getGameUser().LoginUID);
				pr.writeInt(r.slots[i].afk);
			}
		
		for( int i=0; i<6; i++ )
			if( r.slots[i].isActive == true )
				r.slots[i].client.sendPacket(pr, false);
	}

	public static void EVENT_END_GAME_REQ(GameUser u, Packet p) {
		Room r = u.CurrentRoom;
		
		if( r == null )
			return;
		
		// 너무 길어서 한놈꺼만 읽어서 승/패 팀만 파악한다
		
		int numGameResult = p.readInt(); // m_vecGameResult 의 벡터 사이즈
		String id = p.readUnicodeStringWithLength();
		int uid = p.readInt();
		int baseGP = p.readInt();
		int recvGP = p.readInt();
		int contPoint = p.readInt();
		boolean bWin = p.readBool(); // 이걸로 승패 가리자..
		
		// 방에서 위 유저의 팀을 검색해서 승패팀 가리자
		boolean isBlueWin = false;
		for( int i=0; i<6; i++ )
			if( r.slots[i].isActive == true)
				if( r.slots[i].client.getGameUser().LoginUID == uid ) {
					if( bWin == true && i < 3 ) // 012가 이기면 세르딘 승리
						isBlueWin = false;
					else if( bWin == true && i >= 3) // 345가 이기면 카나반 승리 
						isBlueWin = true;
					else if( bWin == false && i < 3 ) // 012가 지면 카나반 승리
						isBlueWin = true;
					else if( bWin == false && i >=3 ) // 345가 지면 세르딘 승리
						isBlueWin = false;
				}
		
		System.out.println("bWin = " + bWin);
		System.out.println("isBlueWin = " + isBlueWin);
		
		Packet pr = new Packet(OpcodeGame.EVENT_END_GAME_BROAD);
		
		pr.writeInt( r.GetRoomHost().getGameUser().LoginUID );
		
		// std::vector<KGameResultOut> m_vecGameResult
		pr.writeInt( r.GetPlayerCount() );
		for( int i=0; i<6; i++ )
			if( r.slots[i].isActive == true ) {
				GameUser slotuser = r.slots[i].client.getGameUser();
				
				pr.writeUnicodeStringWithLength( slotuser.ID );
				pr.writeInt( slotuser.LoginUID );
				pr.writeInt( slotuser.GamePoint );
				pr.writeInt( slotuser.GamePoint ); // base gp
				pr.writeInt( 0 ); // total recv gp
				pr.writeInt(0); // std::map<int,float> m_mapGpBoost
				pr.writeInt( 0 ); // 길드 uid
				pr.writeInt( 0 ); // 기여도
				pr.writeInt( 0 ); // m_vecWin
				pr.writeInt( 0 ); // m_vecLose
				pr.writeInt( 0 ); // m_iIndigoWin
				pr.writeInt( 0 ); // m_iIndigoLose
				pr.writeInt( 0 ); // 길드 포인트
				pr.writeInt( 0 ); // KItem 벡터
				pr.writeInt( 0 ); // 랭킹 포인트
				
				//pr.writeInt( 0 ); // 맵 난이도 맵 std::map<int,KDungeonUserInfo>
				DungeonUserInfo.write_mapDifficulty(pr);
				
				pr.write(0); // 캐릭터 타입
				pr.writeInt( 0 ); // 펫 정보 벡터
				pr.writeInt( 0 ); // 경험치 정보 벡터
				pr.writeInt( 0 ); // 미션 정보 벡터
				pr.writeInt( 0 ); // m_mapObtainCount
				pr.writeInt( 0 ); // KQuickSlot m_vecItemSlot
				pr.writeInt( 0 ); // KQuickSlot m_vecEmoticonSlot
				pr.writeInt( 0 ); // 스테이지 보너스 gp
				pr.writeInt( 0 ); // 컬렉션 벡터
				pr.writeInt( 0 ); // m_kSHDrop / m_vecDropItems
				pr.writeInt( 0 ); // m_kSHDrop / m_vecDropPostItems
				pr.writeInt( 0 ); // m_kSHDrop / m_vecAutoMission
				pr.writeInt( 0 ); // 랭크 포인트
				
				if( i < 3 ) // 세르딘
					if( isBlueWin == true ) // 카나반이 승리?
						pr.writeBool( false );
					else
						pr.writeBool( true );
				else // 카나반
					if( isBlueWin == true ) // 카나반이 승리?
						pr.writeBool( true );
					else
						pr.writeBool( false );
				
				pr.writeInt( 0 ); // 킬
				pr.writeInt( 0 ); // 데스
				pr.writeInt( 0 ); // 스코어
				pr.writeShort( 1 ); // 상자 개수
				pr.writeInt( 0 ); // 길드 배틀포인트
				pr.writeInt( 0 ); // 길드 배틀포인트 획득량
				pr.write( 0 ); // 길드 배틀포인트 채널등급
				pr.writeInt( 0 ); // 결과보상 맵
			}
		
		pr.writeBool(false); // 채팅이벤트 보상
		pr.writeShort(r.RoomID);
		pr.writeInt(0); // m_setKillMonster
		pr.writeInt(0); // m_iQuestEndingStage
		pr.writeInt(0); // m_dwElapsedSec
		pr.writeInt(0); // m_uiMVP
		pr.writeBool(isBlueWin);
		
		// m_vecIsPlayerWin
		pr.writeInt( 6 );
		for( int i=0; i<6; i++ )
			if( r.slots[i].isActive == true )
				if( i < 3 ) // 세르딘
					if( isBlueWin == true ) // 카나반이 승리?
						pr.writeBool( false );
					else
						pr.writeBool( true );
				else // 카나반
					if( isBlueWin == true ) // 카나반이 승리?
						pr.writeBool( true );
					else
						pr.writeBool( false );
			else
				pr.writeBool( false ); // 사람이 없으므로.
		
		pr.writeBool(false); // m_bIsSpecialDropCharBox
		pr.writeInt(0); // m_vecGuildBPoint
		pr.writeBool(false); // m_bNationReward
		pr.writeBool(false); // m_bShDrop
		pr.writeInt(0); // m_nRewardType
		pr.writeInt(0); // m_setHackingUserList
		
		for( int i=0; i<6; i++ )
			if( r.slots[i].isActive == true )
				r.slots[i].client.sendPacket(pr, true);
		
		r.isPlaying = false;
	}

	public static void EVENT_SET_PRESS_STATE_REQ(GameUser u, Packet p) {
		Room r = u.CurrentRoom;
		
		if( r == null )
			return;
		
		int state = p.readInt();
		
		Packet pr = new Packet(OpcodeGame.EVENT_PRESS_STATE_NOT);
		
		pr.writeInt(u.LoginUID);
		pr.writeInt(state);
		
		for( int i=0; i<6; i++ )
			if( r.slots[i].isActive == true )
				r.slots[i].client.sendPacket(pr, false);
	}

	public static void EVENT_JOIN_ROOM_INFO_DIVIDE_REQ(GameUser u, Packet p) {		
		Room r = u.CurrentRoom;
		if( r == null )
			return;
		
		int reqMax = p.readInt();
		int reqCurrent = p.readInt();
		
		int index = -1;
		
		for( int i=0; i<6; i++ )
			if( r.slots[i].isActive == true ) {
				index++;
				
				if( index == reqCurrent ) {
					Packet pFirst = new Packet(OpcodeGame.EVENT_JOIN_ROOM_INFO_DIVIDE_ACK);
					pFirst.writeInt(0);
					pFirst.writeInt(r.GetPlayerCount());
					pFirst.writeInt(index);
					KInDoorUserInfo.write_KInDoorUserInfo( r.slots[i].client.getGameUser(), pFirst );
					
					u.parent.sendPacket(pFirst, true);
					break;
				}
			}
		
		if( reqCurrent + 1 == reqMax ) {
			Packet pBroad = new Packet(OpcodeGame.EVENT_JOIN_ROOM_BROAD);
			KInDoorUserInfo.write_KInDoorUserInfo(u, pBroad);
			
			for( int i=0; i<6; i++ ) 
				if( r.slots[i].isActive == true && r.slots[i].client != u.parent ) 
					r.slots[i].client.sendPacket(pBroad, false);
		}
	}

	public static void EVENT_CHANGE_ROOM_OPTION_REQ(GameUser u, Packet p) {
		Room r = u.CurrentRoom;
		
		if( r == null )
			return;
		
		if( r.GetRoomHost() != u.parent )
			return;
		
		int ok = p.readInt();
		String roomtitle = p.readUnicodeStringWithLength();
		String roompw = p.readUnicodeStringWithLength();
		int death_time = p.readInt();
		int death_kill = p.readInt();
		boolean death_intrude = p.readBool();
		boolean death_balance = p.readBool();
		
		r.RoomName = roomtitle;
		r.RoomPass = roompw;
		r.m_nDeathLimitTime = death_time;
		r.m_nDeathKillCount = death_kill;
		r.m_bDeathMatchIntrudable = death_intrude;
		r.m_bDeathMatchBalancing = death_balance;
		
		Packet pBroad = new Packet(OpcodeGame.EVENT_CHANGE_ROOM_OPTION_BROAD);

		pBroad.writeInt(ok);
		pBroad.writeUnicodeStringWithLength(roomtitle);
		pBroad.writeUnicodeStringWithLength(roompw);
		pBroad.writeInt(death_time);
		pBroad.writeInt(death_kill);
		pBroad.writeBool(death_intrude);
		pBroad.writeBool(death_balance);
		
		for( int i=0; i<6; i++ ) 
			if( r.slots[i].isActive == true ) 
				r.slots[i].client.sendPacket(pBroad, false);
	}

	public static void EVENT_BAN_USER_REQ(GameUser u, Packet p) {
		int ok = p.readInt();
		String ID = p.readUnicodeStringWithLength();
		
		Room r = u.CurrentRoom;
		
		if( r == null )
			return;
		
		if( r.GetRoomHost() != u.parent )
			return;
		
		// ID로 슬롯 찾기
		int slot = -1;
		for( int i=0; i<6; i++ )
			if( r.slots[i].isActive == true )
				if( r.slots[i].client.getGameUser().ID.equals(ID) )
					slot = i;
		
		if( slot == -1 )
			return;
		
		r.leftbancount = r.leftbancount - 1;
		
		r.LeaveRoom( r.slots[slot].client.getGameUser() , r.leftbancount );
	}
	
	public static void write_KRoomInfo(Packet p, Room r) {
		p.writeShort(r.RoomID);
		p.writeUnicodeStringWithLength(r.RoomName);
		p.write(0); // public
		p.write(0); // guild
		p.writeUnicodeStringWithLength(r.RoomPass);
		p.writeShort(r.GetPlayerCount());
		p.writeShort( r.GetPlayerCount() + r.GetFreeSlotCount() );
		p.writeShort(1);
		p.write(r.GameCategory);
		p.writeInt(r.GameMode);
		p.writeInt(r.ItemMode);
		p.writeBool(r.RandomMap);
		p.writeInt(r.GameMap);
		p.writeInt(12); // 프로토콜 강제
		for( int i=0; i<6; i++ )
			p.writeBool( r.slots[i].isOpen );
		
		p.writeInt(-1); // 몬스터ID
		p.writeInt(0); // 몬스터레벨
		p.writeInt(r.GameDifficulty);
		p.write(1); // 아이템 루팅 방식
		
		p.writeIntLE( Convert.IPToInt(r.parentCh.parentServer.UDPRelayIP) );
		p.writeShort( r.parentCh.parentServer.UDPRelayPort );
		p.writeIntLE( Convert.IPToInt(r.parentCh.parentServer.TCPRelayIP) );
		p.writeShort( r.parentCh.parentServer.TCPRelayPort );
		p.writeBool(true); // 플레이 중인 게임에 조인 할수 있는지 정보..
		p.writeBool(true); // 데쓰매치 방에 난입할수 있다. (게임이 아니라 방)
		p.writeBool(true); // 데쓰매치 능력치 밸런싱.
		p.writeInt(300); // 데스매치 시간
		p.writeInt(20); // 데스매치 킬
		p.writeInt(123123); // p2p용 유니크 번호. 방마다 달라야 하나봄
		p.writeLong(0); // m_biStartingTotalExp
		p.writeInt(0); // m_dwStartingTotalGp
		p.writeInt(0); // m_nTotalLv
		p.writeUnicodeStringWithLength(""); // m_pairGuildMarkName left
		p.writeUnicodeStringWithLength(""); // m_pairGuildMarkName right
		p.writeUnicodeStringWithLength(""); // m_pairGuildName left
		p.writeUnicodeStringWithLength(""); // m_pairGuildName right
		p.writeInt(0); // m_pairBattlePoint left
		p.writeInt(0); // m_pairBattlePoint right
		p.writeUnicodeStringWithLength(""); // 방장 국가 코드
		
		p.writeShort(6); // 기본 최대 방원수
		p.writeBool(true); // 모드 변경 가능 여부
		p.writeInt(1); // 결과 보상 type
	}
}
