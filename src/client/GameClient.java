package client;

import java.io.OutputStream;
import java.net.Socket;
import java.security.SecureRandom;
import game.Agit;
import game.Channel;
import game.Dungeon;
import game.GameFunction;
import game.Room;
import game.Shop;
import game.Skill;
import game.Square;
import game.etcclass.KFullSPInfo;
import game.item.PetInfo;
import game.user.GameCharacter;
import game.user.GameUser;
import packet.Crypto;
import packet.OpcodeGame;
import packet.Packet;
import server.Main;
import server.Server;
import server.SocketRecvHandler;
import util.Convert;

public class GameClient extends Client {
	public Server server = null;
	public GameUser client_user = null;
	public GameUser getGameUser() { if( client_user == null ) { client_user = new GameUser(this); } return client_user; }
	public boolean MigrationFlag = false; // 서버 이동중...
	
	public GameClient(Socket s, Server srv)  {
		isClosed = false;
		this.s = s;
		this.server = srv;
		client_type = Client.GAME_CLIENT;
		
		sh = new SocketRecvHandler(this);
		sh.start();
		
		Main.printmsg("[" + server.serverName + "] 새로운 클라이언트 접속 (" + s.getInetAddress().getHostAddress() + ":" + s.getPort() + ")");
		
		// 패킷 암호화 키 초기화
		SecureRandom sr = new SecureRandom();
		sr.nextBytes(packetPrefix);
		sr.nextBytes(packetKey);
		sr.nextBytes(packetHmac);
		
		// 처음 보내는 패킷
		isFirstPacket = true;
		Packet p = new Packet(OpcodeGame.EVENT_ACCEPT_CONNECTION_NOT);
		p.write(packetPrefix);
		p.writeInt(8);
		p.write(packetHmac);
		p.writeInt(8);
		p.write(packetKey);
		p.writeInt(1);
		p.skip(8);
		sendPacket(p, false);
		p = null;
		isFirstPacket = false;
		
		Main.printmsg("    DES KEY: " + Convert.byteArrayToHexString(packetKey));
	}
	
	// 패킷 보내기
	public void sendPacket(Packet p, boolean compress) {
		p.applyWrite();
		
		byte[] sendbuffer = null;
		if( isFirstPacket )
			sendbuffer = Crypto.AssemblePacket(p, Crypto.GC_DES_KEY, Crypto.GC_HMAC_KEY, new byte[] {0, 0}, packetNum++, compress);
		else
			sendbuffer = Crypto.AssemblePacket(p, packetKey, packetHmac, packetPrefix, packetNum++, compress);
		
		try {
			OutputStream os = s.getOutputStream();
			os.write(sendbuffer);
			os.flush();
		}catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	public void sendPacket(Packet p, boolean compress, boolean output) {
		sendPacket(p, compress);
		
		if( output )
			System.out.println( Convert.byteArrayToHexString(p.buffer) );
	}
	
	// 패킷 수신
	public void onPacket(Packet p) {
		Crypto.DecryptPacket(p, packetKey);
		
		int Opcode = p.readShort();
		int dataSize = p.readInt();
		int isCompressed = p.readByte();
		if( isCompressed == 1 ) p.readInt();
		
		//Main.printmsg(Opcode + "패킷 수신 (" + dataSize + "바이트)\n" + Convert.byteArrayToHexString(p.buffer));
		boolean isProcess = true;
		
		switch( Opcode ) {
		case OpcodeGame.HEART_BIT_NOT: // 하트비트
			heartbit = System.currentTimeMillis();
			break;
		case OpcodeGame.EVENT_VERIFY_ACCOUNT_REQ: // 로그인
			getGameUser().EVENT_VERIFY_ACCOUNT_REQ(p);
			break;
		case OpcodeGame.EVENT_CHAR_SELECT_JOIN_REQ: // 캐릭터 접속
			getGameUser().EVENT_CHAR_SELECT_JOIN_REQ(p);
			break;
		case OpcodeGame.EVENT_GET_FULL_SP_INFO_REQ: // 0x01A7
			//GameFunction.EVENT_GET_FULL_SP_INFO_REQ(getGameUser());
			KFullSPInfo.send_EVENT_GET_FULL_SP_INFO_ACK(getGameUser());
			break;
		case OpcodeGame.EVENT_PET_COSTUM_LIST_REQ: // 0x0205
			GameFunction.EVENT_PET_COSTUM_LIST_REQ(getGameUser());
			break;
		case OpcodeGame.EVENT_INVEN_BUFF_ITEM_LIST_REQ: // 0x04CA
			GameFunction.EVENT_INVEN_BUFF_ITEM_LIST_REQ(getGameUser());
			break;
		case OpcodeGame.EVENT_DEPOT_INFO_REQ: // 0x053C
			GameFunction.EVENT_DEPOT_INFO_REQ(getGameUser());
			break;
		case OpcodeGame.EVENT_STAT_CLIENT_INFO: // 0x00E2
			GameFunction.EVENT_STAT_CLIENT_INFO(getGameUser(), p);
			break;
		case OpcodeGame.EVENT_COST_RATE_FOR_GAMBLE_BUY_REQ: // 0x0367
			GameFunction.EVENT_COST_RATE_FOR_GAMBLE_BUY_REQ(getGameUser());
			break;
		case OpcodeGame.EVENT_SET_IDLE_STATE_REQ: // 0x0343
			GameFunction.EVENT_SET_IDLE_STATE_REQ(getGameUser());
			break;
		case OpcodeGame.EVENT_CHOICE_BOX_LIST_REQ: // 0x03F4
			GameFunction.EVENT_CHOICE_BOX_LIST_REQ(getGameUser());
			break;
		case OpcodeGame.EVENT_EXP_POTION_LIST_REQ: // 0x053A
			GameFunction.EVENT_EXP_POTION_LIST_REQ(getGameUser());
			break;
		case OpcodeGame.EVENT_AGIT_STORE_CATALOG_REQ: // 0x045A
			GameFunction.EVENT_AGIT_STORE_CATALOG_REQ(getGameUser());
			break;
		case OpcodeGame.EVENT_AGIT_MAP_CATALOGUE_REQ: // 0x0452
			GameFunction.EVENT_AGIT_MAP_CATALOGUE_REQ(getGameUser());
			break;
		case OpcodeGame.EVENT_AGIT_STORE_MATERIAL_REQ: // 0x045C
			GameFunction.EVENT_AGIT_STORE_MATERIAL_REQ(getGameUser());
			break;
		case OpcodeGame.EVENT_FAIRY_TREE_LV_TABLE_REQ: // 0x04A0
			GameFunction.EVENT_FAIRY_TREE_LV_TABLE_REQ(getGameUser());
			break;
		case OpcodeGame.EVENT_INVITE_DENY_NOT: // 0x015C
			getGameUser().boolDenyInvite = p.readBool();
			break;
		case OpcodeGame.EVENT_GET_USER_DONATION_INFO_REQ: // 0x020B
			GameFunction.EVENT_GET_USER_DONATION_INFO_REQ(getGameUser());
			break;
		case OpcodeGame.EVENT_RECOMMEND_FULL_INFO_REQ: // 0x0237
			GameFunction.EVENT_RECOMMEND_FULL_INFO_REQ(getGameUser());
			break;
		case OpcodeGame.EVENT_USER_BINGO_DATA_REQ: // 0x028E
			GameFunction.EVENT_USER_BINGO_DATA_REQ(getGameUser());
			break;
		case OpcodeGame.EVENT_CHANNEL_LIST_REQ: // 0x000E
			GameFunction.EVENT_CHANNEL_LIST_REQ(getGameUser());
			break;
		case OpcodeGame.EVENT_DONATION_INFO_REQ: // 0x020D
			GameFunction.EVENT_DONATION_INFO_REQ(getGameUser());
			break;
		case OpcodeGame.EVENT_ENTER_CHANNEL_REQ: // 0x000C
			Channel.EVENT_ENTER_CHANNEL_REQ(getGameUser(), p);
			break;
		case OpcodeGame.EVENT_LEAVE_CHANNEL_NOT: // 0x001A
			Channel.EVENT_LEAVE_CHANNEL_NOT(getGameUser());
			break;
		case OpcodeGame.EVENT_CHAT_REQ: // 0x001A 채팅
			Channel.EVENT_CHAT_REQ(getGameUser(), p);
			break;
		case OpcodeGame.EVENT_WHISPER_REQ: // 귓속말
			Channel.EVENT_WHISPER_REQ(getGameUser(), p);
			break;
		case OpcodeGame.EVENT_ROOM_LIST_REQ: // 방 목록
			Channel.EVENT_ROOM_LIST_REQ(getGameUser(), p);
			break;
		case OpcodeGame.EVENT_CREATE_ROOM_REQ: // 방 만들기
			Channel.EVENT_CREATE_ROOM_REQ(getGameUser(), p);
			break;
		case OpcodeGame.EVENT_LEAVE_ROOM_REQ: // 방 나가기
			Room.EVENT_LEAVE_ROOM_REQ(getGameUser(), p);
			break;
		case OpcodeGame.EVENT_LEAVE_GAME_REQ: // 게임 나가기
			Room.EVENT_LEAVE_GAME_REQ(getGameUser(), p);
			break;
		case OpcodeGame.EVENT_CHANGE_ROOM_INFO_REQ: // 방 게임정보 바뀜
			Room.EVENT_CHANGE_ROOM_INFO_REQ(getGameUser(), p);
			break;
		case OpcodeGame.EVENT_JOIN_ROOM_REQ: // 방 입장
			Room.EVENT_JOIN_ROOM_REQ(getGameUser(), p);
			break;
		case OpcodeGame.EVENT_CHANGE_ROOMUSER_INFO_REQ: // 방 유저정보 바뀜
			Room.EVENT_CHANGE_ROOMUSER_INFO_REQ(getGameUser(), p);
			break;
		case OpcodeGame.EVENT_EQUIP_ITEM_REQ: // 장비 변경 요청
			GameCharacter.EVENT_EQUIP_ITEM_REQ(getGameUser(), p);
			break;
		case OpcodeGame.EVENT_SKILL_TRAINING_REQ: // 스킬 배우기 요청
			Skill.EVENT_SKILL_TRAINING_REQ(getGameUser(), p);
			break;
		case OpcodeGame.EVENT_SET_SKILL_REQ: // 스킬 장착 요청
			Skill.EVENT_SET_SKILL_REQ(getGameUser(), p);
			break;
		case OpcodeGame.EVENT_REMOVE_SKILL_REQ: // 스킬 취소 요청
			Skill.EVENT_REMOVE_SKILL_REQ(getGameUser(), p);
			break;
		case OpcodeGame.EVENT_START_GAME_REQ: // 게임시작
			Room.EVENT_START_GAME_REQ(getGameUser(), p);
			break;
		case OpcodeGame.EVENT_RELAY_LOADING_STATE: // 게임시작 릴레이 로딩
			Room.EVENT_RELAY_LOADING_STATE(getGameUser(), p);
			break;
		case OpcodeGame.EVENT_LOAD_COMPLETE_NOT: // 게임시작 로딩 완료
			Room.EVENT_LOAD_COMPLETE_NOT(getGameUser(), p);
			break;
		case OpcodeGame.EVENT_STAGE_LOAD_COMPLETE_NOT: // 스테이지 로딩 완료
			Room.EVENT_STAGE_LOAD_COMPLETE_NOT(getGameUser(), p);
			break;
		case OpcodeGame.EVENT_ROOM_MEMBER_PING_INFO_REQ: // 핑 정보
			Room.EVENT_ROOM_MEMBER_PING_INFO_REQ(getGameUser(), p);
			break;
		case OpcodeGame.EVENT_GET_ROOMUSER_IDLE_STATE_REQ: // 자리비움
			Room.EVENT_GET_ROOMUSER_IDLE_STATE_REQ(getGameUser(), p);
			break;
		case OpcodeGame.EVENT_END_GAME_REQ: // 게임종료
			Room.EVENT_END_GAME_REQ(getGameUser(), p);
			break;
		case OpcodeGame.EVENT_SET_PRESS_STATE_REQ: // 
			Room.EVENT_SET_PRESS_STATE_REQ(getGameUser(), p);
			break;
		case OpcodeGame.EVENT_CASHBACK_EXTRA_RATIO_INFO_REQ: //
			Shop.EVENT_CASHBACK_EXTRA_RATIO_INFO_REQ( getGameUser(), p );
			break;
		case OpcodeGame.EVENT_PACKAGE_INFO_REQ: //
			Shop.EVENT_PACKAGE_INFO_REQ( getGameUser(), p );
			break;
		case OpcodeGame.EVENT_PACKAGE_INFO_DETAIL_REQ: //
			Shop.EVENT_PACKAGE_INFO_DETAIL_REQ( getGameUser(), p );
			break;
		case OpcodeGame.EVENT_ITEM_BUY_CHECK_REQ: //
			Shop.EVENT_ITEM_BUY_CHECK_REQ( getGameUser(), p );
			break;
		case OpcodeGame.EVENT_BUY_VIRTUAL_CASH_REQ: //
			Shop.EVENT_BUY_VIRTUAL_CASH_REQ( getGameUser(), p );
			break;
		case OpcodeGame.EVENT_BUY_FOR_GP_REQ: // 아이템 구입
			Shop.EVENT_BUY_FOR_GP_REQ( getGameUser(), p );
			break;
		case OpcodeGame.EVENT_JOIN_ROOM_INFO_DIVIDE_REQ: // 다음 유저 정보 주세요...
			Room.EVENT_JOIN_ROOM_INFO_DIVIDE_REQ(getGameUser(), p);
			break;
		case OpcodeGame.EVENT_CLIENT_PING_REPORT_NOT:
			GameFunction.EVENT_CLIENT_PING_REPORT_NOT(getGameUser(), p);
			break;
		case OpcodeGame.EVENT_SERVER_MIGRATION_REQ: // 서버 이동~
			GameFunction.EVENT_SERVER_MIGRATION_REQ(getGameUser(), p);
			break;
		case OpcodeGame.EVENT_ENTER_AGIT_REQ: // 아지트
			Agit.EVENT_ENTER_AGIT_REQ(getGameUser(), p);
			break;
		case OpcodeGame.EVENT_DUNGEON_REWARD_EXP_REQ: // 던전에서 몹 때려잡아서 경험치 받음
			Dungeon.EVENT_DUNGEON_REWARD_EXP_REQ(getGameUser(), p);
			break;
		case OpcodeGame.EVENT_SPECIAL_REWARD_REQ: // 보상 선택 (상자인듯)
			Dungeon.EVENT_SPECIAL_REWARD_REQ(getGameUser(), p);
			break;
		case OpcodeGame.EVENT_CHANGE_ROOM_OPTION_REQ: // 방 옵션 변경
			Room.EVENT_CHANGE_ROOM_OPTION_REQ(getGameUser(), p);
			break;
		case OpcodeGame.EVENT_CREATE_PET_REQ: // 펫 등록
			PetInfo.EVENT_CREATE_PET_REQ(getGameUser(), p);
			break;
		case OpcodeGame.EVENT_CHANGE_PET_NAME_REQ: // 펫 닉네임 변경
			PetInfo.EVENT_CHANGE_PET_NAME_REQ(getGameUser(), p);
			break;
		case OpcodeGame.EVENT_UPDATE_DEPOT_DATA_REQ:
			GameFunction.EVENT_UPDATE_DEPOT_DATA_REQ(getGameUser(), p);
			break;
		case OpcodeGame.EVENT_SELL_INFO_REQ: // 수량 판매 리스트에 넣으면..
			Shop.EVENT_SELL_INFO_REQ(getGameUser(), p);
			break;
		case OpcodeGame.EVENT_BUNDLE_SELL_ITEM_REQ: // 아이템 판매
			Shop.EVENT_BUNDLE_SELL_ITEM_REQ(getGameUser(), p);
			break;
		case OpcodeGame.EVENT_BAN_USER_REQ: // 방에서 강퇴
			Room.EVENT_BAN_USER_REQ(getGameUser(), p);
			break;
		case OpcodeGame.EVENT_USE_CHANGE_NICKNAME_REQ: // 닉네임 바꾸기
			GameUser.EVENT_USE_CHANGE_NICKNAME_REQ(getGameUser(), p);
			break;
		case OpcodeGame.EVENT_USE_BONUS_POINT_REQ: // 던전 보너스 사용해서 부활
			GameUser.EVENT_USE_BONUS_POINT_REQ(getGameUser(), p);
			break;
		case OpcodeGame.EVENT_UNLOCK_CHANGE_WEAPON_REQ: // 무기체인지 슬롯 열기
			GameCharacter.EVENT_UNLOCK_CHANGE_WEAPON_REQ(getGameUser(), p);
			break;
		case OpcodeGame.EVENT_SQUARE_LIST_REQ: // 광장 리스트 요청
			Square.EVENT_SQUARE_LIST_REQ(getGameUser(), p);
			break;
		case OpcodeGame.EVENT_ENTER_SQUARE_REQ: // 광장 입장 요청
			Square.EVENT_ENTER_SQUARE_REQ(getGameUser(), p);
			break;
		case OpcodeGame.EVENT_OPEN_CALENDAR_BONUS_POINT_REQ:
			GameFunction.EVENT_OPEN_CALENDAR_BONUS_POINT_REQ(getGameUser(), p);
			break;
		case OpcodeGame.EVENT_SQUARE_LOADING_COMPLETE_REQ: // 광장 입장 요청
			Square.EVENT_SQUARE_LOADING_COMPLETE_REQ(getGameUser(), p);
			break;
		case OpcodeGame.EVENT_SQUARE_USER_SYNC_REQ: // 광장 싱크 요청
			Square.EVENT_SQUARE_USER_SYNC_REQ(getGameUser(), p);
			break;
		default:
			isProcess = false;
			break;
		}
		
		if( isProcess ) {
			//Main.printmsg("패킷 처리 완료 (OP:" + Opcode + ", SIZE: " + dataSize + "byte, ID: " + getGameUser().ID + ")");
		} else {
			Main.printmsg("정의되지 않은 패킷 수신 (OP:" + Opcode + ", SIZE: " + dataSize + "byte)\n" + Convert.byteArrayToHexString(p.buffer));
		}
	}
	
	public void SaveDataForClose() {	
		// 스킬트리 저장하자
		Skill.SaveSkillSet( getGameUser() );
	}
	
	@Override
	public void close() {
		// 서버 이동이 아니라 게임을 끈경우
		if( MigrationFlag == false )
			SaveDataForClose();
		MigrationFlag = false;
		
		// 방에 들어가있으면 나가게 만들어주자
		if( getGameUser().CurrentRoom != null )
			getGameUser().CurrentRoom.LeaveRoom( getGameUser() );
		
		// 채널 나간다
		getGameUser().LeaveCh();
		
		// 다른 사람들한테 로그아웃을 알려주장
		for( int i=0; i<server.clients.size(); i++ ) {
			GameClient target = (GameClient)server.clients.get(i);
			
			// 이미 세션이 끊어진 애들도 있어서 try-catch
			try {
				if( target != null )
					target.getGameUser().SendChat("플지컴서버", "", "#cFFFF00" + getGameUser().Nick + "#cFFFF00님께서 서버 이동 또는 로그아웃 하셨습니다.#cX");
			}catch(Exception e) { }
		}
		
		Main.printmsg("클라이언트 접속 해제 (" + s.getInetAddress().getHostAddress() + ":" + s.getPort() + ")");
		try { sh.interrupt(); } catch(Exception e) { }
		try { s.close(); } catch(Exception e) { }
		isClosed = true; // 끊어졌다고 표시한다. PingPong이 객체 삭제해야함.
		s = null;
		sh = null;
	}
}
