package login;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import client.LoginClient;
import game.guild.GuildInfo;
import game.guild.GuildUserInfo;
import packet.OpcodeLogin;
import packet.Packet;
import server.Main;
import util.Database;
import util.Ini;

public class AllFunction {
	public static void sendClientContentsFirstInitInfo(LoginClient c) {
		Packet p = new Packet(OpcodeLogin.ENU_CLIENT_CONTENTS_FIRST_INIT_INFO_ACK);
		
		p.writeInt(0); // 0 = ECCFII_CONNECTED / 1 = ECCFII_DATA_CHANGED
		
		// pair...
		p.writeInt(5); // 아마 맞을듯;;
		p.writeInt(0); // m_mapLoadingImageName
		p.writeInt(1); // m_vecNewsNumber
		p.writeInt(2); // m_mapPVPLoadingImageName
		p.writeInt(3); // m_vecEventBannerInfo
		p.writeInt(4); // m_mapScriptName
		
		// map : m_mapLoadingImageName
		p.writeInt(1); // map size 하나밖에 없다. 두개보내면 랜덤일지도
		p.writeInt(0); // map index
		p.writeInt(4); // vector size
		p.writeUnicodeStringWithLength("Load1_1.dds");
		p.writeUnicodeStringWithLength("Load1_2.dds");
		p.writeUnicodeStringWithLength("Load1_3.dds");
		p.writeUnicodeStringWithLength("LoadGauge.dds");
		
		// vector : m_vecNewsNumber
		p.writeInt(1); // vector size
		p.writeInt(0); // news1
		//p.writeInt(1); // news2
		
		// map : m_mapPVPLoadingImageName
		p.writeInt(1); // map size 또 하나다
		p.writeInt(0); // map index
		p.writeInt(3); // vector size
		p.writeUnicodeStringWithLength("ui_match_load1.dds");
		p.writeUnicodeStringWithLength("ui_match_load2.dds");
		p.writeUnicodeStringWithLength("ui_match_load3.dds");
		
		// vector : m_vecEventBannerInfo
		p.writeInt(1); // vector size
		p.writeUnicodeStringWithLength("ui_match_load1.dds"); // name
		p.writeInt(0); // what
		
		// map : m_mapScriptName
		p.writeInt(3); // map size
		p.writeInt(0); // map index
		p.writeUnicodeStringWithLength("Square.lua");
		p.writeInt(1); // map index
		p.writeUnicodeStringWithLength("SquareObject.lua");
		p.writeInt(2); // map index
		p.writeUnicodeStringWithLength("Square3DObject.lua");
		
		// pair...
		p.writeInt(3); // 아마 맞을듯;;
		p.writeInt(0); // m_vecExceptionMotionID
		p.writeInt(1); // m_setDLLBlackList
		p.writeInt(2); // m_vecExtendSHAList
		
		// vector<integer> : m_vecExceptionMotionID
		p.writeInt(0); // 없다.
		
		// set<wide string> : m_setDLLBlackList
		p.writeInt(2); // size
		p.writeUnicodeStringWithLength("test.dll");
		p.writeUnicodeStringWithLength("d3d9.dll");
		
		// vector<wide string> : m_vecExtendSHAList
		p.writeInt(1); // 테스트
		p.writeUnicodeStringWithLength("Stage\\AI\\AI.kom");
		
		c.sendPacket(p, true);
	}
	
	public static void sendShaFileList(LoginClient c) {
		Packet p = new Packet(OpcodeLogin.ENU_SHAFILENAME_LIST_ACK);
		
		p.writeInt(0);
		
		int shafile_count = Integer.parseInt( Ini.getIni("login.shafile.count") );
		p.writeInt(shafile_count);
		
		for(int i=1; i<=shafile_count; i++) {
			String shaFile = Ini.getIni("login.shafile.name" + i);
			p.writeInt(shaFile.length() * 2);
			p.writeUnicodeString(shaFile);
		}
		
		c.sendPacket(p, false);
	}
	
	public static void onLogin(LoginClient c, Packet p) {
		/*
			0 성공 / 1 Protocol Version이 틀림 / 2 아직 행동이 정의되지 않은 인증방식
			3 인증 실패 / 4 rename 실패 / 5 이중접속 감지 / 6 Center Gash 에서 Login 을 얻을수 없다
			7 ID or Password에 이상한 문자들어가있음 / 8 요청한 유저와 반환 유저가 다름_Gash_
			9 Login 길이가 0 이다 / 10 틀린 Password / 11 존재하지 않는 계정
			12 신규 유저 등록 실패 / 13 연령 제한 / 14 접속 아이피 제한 / 15 스크립트 체크섬 오류. 
			16 패치시간 중엔 접속할 수 없음. / 17 등록되지 않은 Admin IP.
			18 빈펀, 게쉬 OTP 오류 / 19 빈펀 전환 유저가 기존방식으로 접속함.
			20 미국 웹 인증 실패 / 21 블럭 된 유저 / 22 알수 없는 PostFix. / 23 중복된 이메일.
			24 유저 정보 없음. / 25 남미 인증서버 오류. / 26 남미 인증 실패.
			27 알수 없는 WebService. / 28 블럭된 유저정보.

		 */
		
		String ID = p.readStringWithLength();
		String PW = p.readStringWithLength();
		String IP = p.readUnicodeStringWithLength();
		int ProtocolVer = p.readInt();
		
		Main.printmsg("로그인 요청 (" + ID + ", " + PW + ")");
		
		// 데이터
		String nick = null;
		int AuthLevel = 0;
		int LoginUID = 0;

		Connection con = Database.getConnection();
		try {
			PreparedStatement ps = con.prepareStatement("SELECT * FROM account WHERE Login = ? AND Passwd = ?");
			ps.setString(1, ID);
			ps.setString(2, PW);
			ResultSet rs = ps.executeQuery();
			
			// 로그인 실패
			if( rs.first() == false ) {
				Main.printmsg("    로그인 실패");

				Packet pLoginfail = new Packet(OpcodeLogin.ENU_VERIFY_ACCOUNT_ACK);
				pLoginfail.writeInt(10); // 로그인 실패
				pLoginfail.writeUnicodeStringWithLength(ID);
				pLoginfail.writeStringWithLength(PW);
				pLoginfail.write(0);
				pLoginfail.writeInt(0);
				
				c.sendPacket(pLoginfail, true);
				
				rs.close();
				ps.close();
				con.close();
				return;
			}
			
			nick = rs.getString("Nick");
			AuthLevel = rs.getInt("AuthLevel");
			LoginUID = rs.getInt("LoginUID");
			
			con.close();
			ps.close();
			rs.close();			
		} catch (Exception e) {
			e.printStackTrace(); return;
		}
		
		// 기본 데이터 보내기
		sendServerListFromDB(c);
        sendChannelNews(c);
        sendItemBuyInfo(c);
        ClientContentsOpen.sendClientOpenContents(c);
        sendSocketTableInfo(c);
        sendCashbackRatioInfo(c);
        
        Packet loginSuccess = new Packet(OpcodeLogin.ENU_VERIFY_ACCOUNT_ACK);
        loginSuccess.writeInt(0);
        loginSuccess.writeUnicodeStringWithLength(ID);
        loginSuccess.writeStringWithLength(PW);
		loginSuccess.write(0); // 성별 
		loginSuccess.writeInt(20); // 나이
		loginSuccess.writeInt(0); // AuthTick
		loginSuccess.writeBool(false); // 신규유저
		loginSuccess.writeBool(false); // 체험계정
		loginSuccess.writeBool(false); // BlockIPPass
		loginSuccess.write(AuthLevel); // AuthLevel
		loginSuccess.writeStringWithLength("ZZ"); // NationCode
		loginSuccess.writeInt(LoginUID); // LoginUID
		GuildUserInfo.write_NoGuildUserInfoPacket( loginSuccess ); // 길드유저정보 귀찮다.
		GuildInfo.write_NoGuildInfoPacket( loginSuccess ); // 길드정보 귀찮다
		GuildInfo.write_NoGuildNoticePacket( loginSuccess );
		loginSuccess.writeInt(0); // map<DWORD, KNGuildUserInfo>
		loginSuccess.writeStringWithLength("http://play.gckom.com/guildmark/"); // 길드마크
		loginSuccess.writeInt(0); // m_nFunBoxBonus
		loginSuccess.writeInt(0); // 남미 PC방 혜택 번호
		loginSuccess.writeInt(0); // m_vecScriptCheckResult
		loginSuccess.writeInt(0); // 채널 타입
		loginSuccess.writeInt(0); // map<char,__int64> m_mapCharExp
		loginSuccess.writeBool(false); // m_bUseLoadCheckInServer
		loginSuccess.writeFloat(0); // 서버 동접 부풀리기, -1은
		loginSuccess.writeInt(0); // 미국 유저 고유값
		loginSuccess.writeLong(0); // 미국 유저 고유값
		loginSuccess.writeUnicodeStringWithLength("..."); // m_wstrFailString
        
		c.sendPacket(loginSuccess, true);
	}
	
	public static void sendServerList(LoginClient c) {
		Packet p = new Packet(OpcodeLogin.ENU_SERVER_LIST_NOT);
		
		int gameserver_count = Integer.parseInt( Ini.getIni("login.gameservercount") );
		
		p.writeInt(gameserver_count);
		
		for(int i=1; i<=gameserver_count; i++) {
			String serverName = Ini.getIni("login.server" + i + ".name");
			String serverDesc = Ini.getIni("login.server" + i + ".desc");
			String serverIP = Ini.getIni("login.server" + i + ".ip");
			String serverPort = Ini.getIni("login.server" + i + ".port");
			
			p.writeInt(i);
			p.writeInt(i);
			p.writeInt(serverName.length() * 2);
			p.writeUnicodeString(serverName);
			p.writeInt(serverIP.length());
			p.writeString(serverIP);
			p.writeShort( Short.parseShort(serverPort) );
			p.writeInt(0); // 현재 인원
			p.writeInt(500); // 수용 가능 인원
			p.writeInt(327); // 프로토콜 버전
			p.writeHexString("FF FF FF FF FF FF FF FF");
			p.writeInt(serverIP.length()); // 또 보냄
            p.writeString(serverIP); // ...
            p.writeInt(serverDesc.length() * 2);
            p.writeUnicodeString(serverDesc);
            p.writeHexString("00 00 00 00");
		}
		
		c.sendPacket(p, false);
	}
	
	public static void sendServerListFromDB(LoginClient c) {
		Packet p = new Packet(OpcodeLogin.ENU_SERVER_LIST_NOT);

		Connection con = Database.getConnection();
 		PreparedStatement ps = null;
 		ResultSet rs = null;
 		try {
 			// 개수 얻기
 			ps = con.prepareStatement("SELECT COUNT(*) AS `count` FROM `gameserver` WHERE `Show` = 1 ORDER BY `Order` ASC");
 			rs = ps.executeQuery();
 			
 			rs.first();
 			int gameserver_count = rs.getInt("count");
 			p.writeInt(gameserver_count);
 			
 			Database.close(null, ps, rs);
 			
 			// 서버 얻기
 			int i = 0;
 			ps = con.prepareStatement("SELECT * FROM `gameserver` WHERE `Show` = 1 ORDER BY `Order` ASC");
 			rs = ps.executeQuery();
 			if( rs.first() == true ) {
 				do {
					String serverName = rs.getString("ServerName");
					String serverDesc = rs.getString("ServerDesc");
					String serverIP = rs.getString("IP");
					short serverPort = rs.getShort("Port");
					
					i++;
					p.writeInt(i);
					p.writeInt(i);
					p.writeInt(serverName.length() * 2);
					p.writeUnicodeString(serverName);
					p.writeInt(serverIP.length());
					p.writeString(serverIP);
					p.writeShort( serverPort );
					p.writeInt(0); // 현재 인원
					p.writeInt(500); // 수용 가능 인원
					p.writeInt(327); // 프로토콜 버전
					p.writeHexString("FF FF FF FF FF FF FF FF");
					p.writeInt(serverIP.length()); // 또 보냄
					p.writeString(serverIP); // ...
					p.writeInt(serverDesc.length() * 2);
					p.writeUnicodeString(serverDesc);
					p.writeInt(0);
 				}while( rs.next() );
 			}
 		}catch(Exception e) {
 			e.printStackTrace();
 		} finally {
 			Database.close(con, ps, rs);
 		}
 		
 		p.writeInt(0);
		
		c.sendPacket(p, false);
	}
	
	public static void sendChannelNews(LoginClient c) {
		Packet p = new Packet(OpcodeLogin.ENU_CHANNEL_NEWS_NOT);
		p.writeHexString("00 00 00 00");
		c.sendPacket(p, false);
	}
	
	public static void sendSocketTableInfo(LoginClient c) {
		Packet p = new Packet(OpcodeLogin.ENU_SOCKET_TABLE_INFO_NOT);
		p.writeHexString("00 00 00 65 00 00 00 00 00 00 00 01 00 00 00 02 00 00 00 03 00 00 00 04 00 00 00 01 00 00 00 01 00 00 00 02 00 00 00 03 00 00 00 04 00 00 00 02 00 00 00 01 00 00 00 02 00 00 00 03 00 00 00 04 00 00 00 03 00 00 00 02 00 00 00 03 00 00 00 04 00 00 00 05 00 00 00 04 00 00 00 02 00 00 00 03 00 00 00 04 00 00 00 05 00 00 00 05 00 00 00 02 00 00 00 03 00 00 00 04 00 00 00 05 00 00 00 06 00 00 00 03 00 00 00 04 00 00 00 05 00 00 00 06 00 00 00 07 00 00 00 03 00 00 00 04 00 00 00 05 00 00 00 06 00 00 00 08 00 00 00 03 00 00 00 04 00 00 00 05 00 00 00 06 00 00 00 09 00 00 00 04 00 00 00 05 00 00 00 06 00 00 00 07 00 00 00 0A 00 00 00 04 00 00 00 05 00 00 00 06 00 00 00 07 00 00 00 0B 00 00 00 04 00 00 00 05 00 00 00 06 00 00 00 07 00 00 00 0C 00 00 00 05 00 00 00 06 00 00 00 07 00 00 00 08 00 00 00 0D 00 00 00 05 00 00 00 06 00 00 00 07 00 00 00 08 00 00 00 0E 00 00 00 05 00 00 00 06 00 00 00 07 00 00 00 08 00 00 00 0F 00 00 00 06 00 00 00 07 00 00 00 08 00 00 00 09 00 00 00 10 00 00 00 06 00 00 00 07 00 00 00 08 00 00 00 09 00 00 00 11 00 00 00 06 00 00 00 07 00 00 00 08 00 00 00 09 00 00 00 12 00 00 00 07 00 00 00 08 00 00 00 09 00 00 00 0A 00 00 00 13 00 00 00 07 00 00 00 08 00 00 00 09 00 00 00 0A 00 00 00 14 00 00 00 07 00 00 00 08 00 00 00 09 00 00 00 0A 00 00 00 15 00 00 00 08 00 00 00 09 00 00 00 0A 00 00 00 0B 00 00 00 16 00 00 00 08 00 00 00 09 00 00 00 0A 00 00 00 0B 00 00 00 17 00 00 00 08 00 00 00 09 00 00 00 0A 00 00 00 0B 00 00 00 18 00 00 00 09 00 00 00 0A 00 00 00 0B 00 00 00 0C 00 00 00 19 00 00 00 09 00 00 00 0A 00 00 00 0B 00 00 00 0C 00 00 00 1A 00 00 00 09 00 00 00 0A 00 00 00 0B 00 00 00 0C 00 00 00 1B 00 00 00 0A 00 00 00 0B 00 00 00 0C 00 00 00 0D 00 00 00 1C 00 00 00 0A 00 00 00 0B 00 00 00 0C 00 00 00 0D 00 00 00 1D 00 00 00 0A 00 00 00 0B 00 00 00 0C 00 00 00 0D 00 00 00 1E 00 00 00 0B 00 00 00 0C 00 00 00 0D 00 00 00 0E 00 00 00 1F 00 00 00 0B 00 00 00 0C 00 00 00 0D 00 00 00 0E 00 00 00 20 00 00 00 0B 00 00 00 0C 00 00 00 0D 00 00 00 0E 00 00 00 21 00 00 00 0C 00 00 00 0D 00 00 00 0E 00 00 00 0F 00 00 00 22 00 00 00 0C 00 00 00 0D 00 00 00 0E 00 00 00 0F 00 00 00 23 00 00 00 0C 00 00 00 0D 00 00 00 0E 00 00 00 0F 00 00 00 24 00 00 00 0D 00 00 00 0E 00 00 00 0F 00 00 00 10 00 00 00 25 00 00 00 0D 00 00 00 0E 00 00 00 0F 00 00 00 10 00 00 00 26 00 00 00 0D 00 00 00 0E 00 00 00 0F 00 00 00 10 00 00 00 27 00 00 00 0E 00 00 00 0F 00 00 00 10 00 00 00 11 00 00 00 28 00 00 00 0E 00 00 00 0F 00 00 00 10 00 00 00 11 00 00 00 29 00 00 00 0E 00 00 00 0F 00 00 00 10 00 00 00 11 00 00 00 2A 00 00 00 0F 00 00 00 10 00 00 00 11 00 00 00 12 00 00 00 2B 00 00 00 0F 00 00 00 10 00 00 00 11 00 00 00 12 00 00 00 2C 00 00 00 0F 00 00 00 10 00 00 00 11 00 00 00 12 00 00 00 2D 00 00 00 10 00 00 00 11 00 00 00 12 00 00 00 13 00 00 00 2E 00 00 00 10 00 00 00 11 00 00 00 12 00 00 00 13 00 00 00 2F 00 00 00 10 00 00 00 11 00 00 00 12 00 00 00 13 00 00 00 30 00 00 00 11 00 00 00 12 00 00 00 13 00 00 00 14 00 00 00 31 00 00 00 11 00 00 00 12 00 00 00 13 00 00 00 14 00 00 00 32 00 00 00 11 00 00 00 12 00 00 00 13 00 00 00 14 00 00 00 33 00 00 00 12 00 00 00 13 00 00 00 14 00 00 00 15 00 00 00 34 00 00 00 12 00 00 00 13 00 00 00 14 00 00 00 15 00 00 00 35 00 00 00 12 00 00 00 13 00 00 00 14 00 00 00 15 00 00 00 36 00 00 00 13 00 00 00 14 00 00 00 15 00 00 00 16 00 00 00 37 00 00 00 13 00 00 00 14 00 00 00 15 00 00 00 16 00 00 00 38 00 00 00 13 00 00 00 14 00 00 00 15 00 00 00 16 00 00 00 39 00 00 00 14 00 00 00 15 00 00 00 16 00 00 00 17 00 00 00 3A 00 00 00 14 00 00 00 15 00 00 00 16 00 00 00 17 00 00 00 3B 00 00 00 14 00 00 00 15 00 00 00 16 00 00 00 17 00 00 00 3C 00 00 00 15 00 00 00 16 00 00 00 17 00 00 00 18 00 00 00 3D 00 00 00 15 00 00 00 16 00 00 00 17 00 00 00 18 00 00 00 3E 00 00 00 15 00 00 00 16 00 00 00 17 00 00 00 18 00 00 00 3F 00 00 00 16 00 00 00 17 00 00 00 18 00 00 00 19 00 00 00 40 00 00 00 16 00 00 00 17 00 00 00 18 00 00 00 19 00 00 00 41 00 00 00 16 00 00 00 17 00 00 00 18 00 00 00 19 00 00 00 42 00 00 00 17 00 00 00 18 00 00 00 19 00 00 00 1A 00 00 00 43 00 00 00 17 00 00 00 18 00 00 00 19 00 00 00 1A 00 00 00 44 00 00 00 17 00 00 00 18 00 00 00 19 00 00 00 1A 00 00 00 45 00 00 00 18 00 00 00 19 00 00 00 1A 00 00 00 1B 00 00 00 46 00 00 00 18 00 00 00 19 00 00 00 1A 00 00 00 1B 00 00 00 47 00 00 00 18 00 00 00 19 00 00 00 1A 00 00 00 1B 00 00 00 48 00 00 00 19 00 00 00 1A 00 00 00 1B 00 00 00 1C 00 00 00 49 00 00 00 19 00 00 00 1A 00 00 00 1B 00 00 00 1C 00 00 00 4A 00 00 00 19 00 00 00 1A 00 00 00 1B 00 00 00 1C 00 00 00 4B 00 00 00 1A 00 00 00 1B 00 00 00 1C 00 00 00 1D 00 00 00 4C 00 00 00 1A 00 00 00 1B 00 00 00 1C 00 00 00 1D 00 00 00 4D 00 00 00 1A 00 00 00 1B 00 00 00 1C 00 00 00 1D 00 00 00 4E 00 00 00 1B 00 00 00 1C 00 00 00 1D 00 00 00 1E 00 00 00 4F 00 00 00 1B 00 00 00 1C 00 00 00 1D 00 00 00 1E 00 00 00 50 00 00 00 1B 00 00 00 1C 00 00 00 1D 00 00 00 1E 00 00 00 51 00 00 00 1C 00 00 00 1D 00 00 00 1E 00 00 00 1F 00 00 00 52 00 00 00 1C 00 00 00 1D 00 00 00 1E 00 00 00 1F 00 00 00 53 00 00 00 1C 00 00 00 1D 00 00 00 1E 00 00 00 1F 00 00 00 54 00 00 00 1D 00 00 00 1E 00 00 00 1F 00 00 00 20 00 00 00 55 00 00 00 1D 00 00 00 1E 00 00 00 1F 00 00 00 20 00 00 00 56 00 00 00 1D 00 00 00 1E 00 00 00 1F 00 00 00 20 00 00 00 57 00 00 00 1E 00 00 00 1F 00 00 00 20 00 00 00 21 00 00 00 58 00 00 00 1E 00 00 00 1F 00 00 00 20 00 00 00 21 00 00 00 59 00 00 00 1E 00 00 00 1F 00 00 00 20 00 00 00 21 00 00 00 5A 00 00 00 1F 00 00 00 20 00 00 00 21 00 00 00 22 00 00 00 5B 00 00 00 1F 00 00 00 20 00 00 00 21 00 00 00 22 00 00 00 5C 00 00 00 1F 00 00 00 20 00 00 00 21 00 00 00 22 00 00 00 5D 00 00 00 20 00 00 00 21 00 00 00 22 00 00 00 23 00 00 00 5E 00 00 00 20 00 00 00 21 00 00 00 22 00 00 00 23 00 00 00 5F 00 00 00 20 00 00 00 21 00 00 00 22 00 00 00 23 00 00 00 60 00 00 00 21 00 00 00 22 00 00 00 23 00 00 00 24 00 00 00 61 00 00 00 21 00 00 00 22 00 00 00 23 00 00 00 24 00 00 00 62 00 00 00 21 00 00 00 22 00 00 00 23 00 00 00 24 00 00 00 63 00 00 00 22 00 00 00 23 00 00 00 24 00 00 00 25 00 00 00 64 00 00 00 22 00 00 00 23 00 00 00 24 00 00 00 25 00 00 00 65 00 00 00 00 00 00 00 BE 00 00 00 FA 00 00 01 4A 00 00 01 D6 00 00 00 01 00 00 00 BE 00 00 00 FA 00 00 01 4A 00 00 01 D6 00 00 00 02 00 00 00 BE 00 00 00 FA 00 00 01 4A 00 00 01 D6 00 00 00 03 00 00 00 FA 00 00 01 4A 00 00 01 D6 00 00 03 52 00 00 00 04 00 00 00 FA 00 00 01 4A 00 00 01 D6 00 00 03 52 00 00 00 05 00 00 00 FA 00 00 01 4A 00 00 01 D6 00 00 03 52 00 00 00 06 00 00 01 4A 00 00 01 D6 00 00 03 52 00 00 08 98 00 00 00 07 00 00 01 4A 00 00 01 D6 00 00 03 52 00 00 08 98 00 00 00 08 00 00 01 4A 00 00 01 D6 00 00 03 52 00 00 08 98 00 00 00 09 00 00 01 D6 00 00 03 52 00 00 08 98 00 00 0E 74 00 00 00 0A 00 00 01 D6 00 00 03 52 00 00 08 98 00 00 0E 74 00 00 00 0B 00 00 01 D6 00 00 03 52 00 00 08 98 00 00 0E 74 00 00 00 0C 00 00 03 52 00 00 08 98 00 00 0E 74 00 00 17 0C 00 00 00 0D 00 00 03 52 00 00 08 98 00 00 0E 74 00 00 17 0C 00 00 00 0E 00 00 03 52 00 00 08 98 00 00 0E 74 00 00 17 0C 00 00 00 0F 00 00 08 98 00 00 0E 74 00 00 17 0C 00 00 22 C4 00 00 00 10 00 00 08 98 00 00 0E 74 00 00 17 0C 00 00 22 C4 00 00 00 11 00 00 08 98 00 00 0E 74 00 00 17 0C 00 00 22 C4 00 00 00 12 00 00 0E 74 00 00 17 0C 00 00 22 C4 00 00 31 9C 00 00 00 13 00 00 0E 74 00 00 17 0C 00 00 22 C4 00 00 31 9C 00 00 00 14 00 00 0E 74 00 00 17 0C 00 00 22 C4 00 00 31 9C 00 00 00 15 00 00 17 0C 00 00 22 C4 00 00 31 9C 00 00 55 F0 00 00 00 16 00 00 17 0C 00 00 22 C4 00 00 31 9C 00 00 55 F0 00 00 00 17 00 00 17 0C 00 00 22 C4 00 00 31 9C 00 00 55 F0 00 00 00 18 00 00 22 C4 00 00 31 9C 00 00 55 F0 00 00 6C 98 00 00 00 19 00 00 22 C4 00 00 31 9C 00 00 55 F0 00 00 6C 98 00 00 00 1A 00 00 22 C4 00 00 31 9C 00 00 55 F0 00 00 6C 98 00 00 00 1B 00 00 31 9C 00 00 55 F0 00 00 6C 98 00 00 84 08 00 00 00 1C 00 00 31 9C 00 00 55 F0 00 00 6C 98 00 00 84 08 00 00 00 1D 00 00 31 9C 00 00 55 F0 00 00 6C 98 00 00 84 08 00 00 00 1E 00 00 55 F0 00 00 6C 98 00 00 84 08 00 00 9C 40 00 00 00 1F 00 00 55 F0 00 00 6C 98 00 00 84 08 00 00 9C 40 00 00 00 20 00 00 55 F0 00 00 6C 98 00 00 84 08 00 00 9C 40 00 00 00 21 00 00 6C 98 00 00 84 08 00 00 9C 40 00 00 B9 28 00 00 00 22 00 00 6C 98 00 00 84 08 00 00 9C 40 00 00 B9 28 00 00 00 23 00 00 6C 98 00 00 84 08 00 00 9C 40 00 00 B9 28 00 00 00 24 00 00 84 08 00 00 9C 40 00 00 B9 28 00 00 D8 CC 00 00 00 25 00 00 84 08 00 00 9C 40 00 00 B9 28 00 00 D8 CC 00 00 00 26 00 00 84 08 00 00 9C 40 00 00 B9 28 00 00 D8 CC 00 00 00 27 00 00 9C 40 00 00 B9 28 00 00 D8 CC 00 00 F8 70 00 00 00 28 00 00 9C 40 00 00 B9 28 00 00 D8 CC 00 00 F8 70 00 00 00 29 00 00 9C 40 00 00 B9 28 00 00 D8 CC 00 00 F8 70 00 00 00 2A 00 00 B9 28 00 00 D8 CC 00 00 F8 70 00 01 1D 28 00 00 00 2B 00 00 B9 28 00 00 D8 CC 00 00 F8 70 00 01 1D 28 00 00 00 2C 00 00 B9 28 00 00 D8 CC 00 00 F8 70 00 01 1D 28 00 00 00 2D 00 00 D8 CC 00 00 F8 70 00 01 1D 28 00 01 47 58 00 00 00 2E 00 00 D8 CC 00 00 F8 70 00 01 1D 28 00 01 47 58 00 00 00 2F 00 00 D8 CC 00 00 F8 70 00 01 1D 28 00 01 47 58 00 00 00 30 00 00 F8 70 00 01 1D 28 00 01 47 58 00 01 7D 40 00 00 00 31 00 00 F8 70 00 01 1D 28 00 01 47 58 00 01 7D 40 00 00 00 32 00 00 F8 70 00 01 1D 28 00 01 47 58 00 01 7D 40 00 00 00 33 00 01 1D 28 00 01 47 58 00 01 7D 40 00 01 B7 74 00 00 00 34 00 01 1D 28 00 01 47 58 00 01 7D 40 00 01 B7 74 00 00 00 35 00 01 1D 28 00 01 47 58 00 01 7D 40 00 01 B7 74 00 00 00 36 00 01 47 58 00 01 7D 40 00 01 B7 74 00 02 00 E4 00 00 00 37 00 01 47 58 00 01 7D 40 00 01 B7 74 00 02 00 E4 00 00 00 38 00 01 47 58 00 01 7D 40 00 01 B7 74 00 02 00 E4 00 00 00 39 00 01 7D 40 00 01 B7 74 00 02 00 E4 00 02 50 F8 00 00 00 3A 00 01 7D 40 00 01 B7 74 00 02 00 E4 00 02 50 F8 00 00 00 3B 00 01 7D 40 00 01 B7 74 00 02 00 E4 00 02 50 F8 00 00 00 3C 00 01 B7 74 00 02 00 E4 00 02 50 F8 00 02 AC C4 00 00 00 3D 00 01 B7 74 00 02 00 E4 00 02 50 F8 00 02 AC C4 00 00 00 3E 00 01 B7 74 00 02 00 E4 00 02 50 F8 00 02 AC C4 00 00 00 3F 00 02 00 E4 00 02 50 F8 00 02 AC C4 00 03 15 10 00 00 00 40 00 02 00 E4 00 02 50 F8 00 02 AC C4 00 03 15 10 00 00 00 41 00 02 00 E4 00 02 50 F8 00 02 AC C4 00 03 15 10 00 00 00 42 00 02 50 F8 00 02 AC C4 00 03 15 10 00 03 BB 78 00 00 00 43 00 02 50 F8 00 02 AC C4 00 03 15 10 00 03 BB 78 00 00 00 44 00 02 50 F8 00 02 AC C4 00 03 15 10 00 03 BB 78 00 00 00 45 00 02 AC C4 00 03 15 10 00 03 BB 78 00 04 27 48 00 00 00 46 00 02 AC C4 00 03 15 10 00 03 BB 78 00 04 27 48 00 00 00 47 00 02 AC C4 00 03 15 10 00 03 BB 78 00 04 27 48 00 00 00 48 00 03 15 10 00 03 BB 78 00 04 27 48 00 04 AC E0 00 00 00 49 00 03 15 10 00 03 BB 78 00 04 27 48 00 04 AC E0 00 00 00 4A 00 03 15 10 00 03 BB 78 00 04 27 48 00 04 AC E0 00 00 00 4B 00 03 BB 78 00 04 27 48 00 04 AC E0 00 05 32 78 00 00 00 4C 00 03 BB 78 00 04 27 48 00 04 AC E0 00 05 32 78 00 00 00 4D 00 03 BB 78 00 04 27 48 00 04 AC E0 00 05 32 78 00 00 00 4E 00 04 27 48 00 04 AC E0 00 05 32 78 00 05 B8 10 00 00 00 4F 00 04 27 48 00 04 AC E0 00 05 32 78 00 05 B8 10 00 00 00 50 00 04 27 48 00 04 AC E0 00 05 32 78 00 05 B8 10 00 00 00 51 00 04 AC E0 00 05 32 78 00 05 B8 10 00 06 3D A8 00 00 00 52 00 04 AC E0 00 05 32 78 00 05 B8 10 00 06 3D A8 00 00 00 53 00 04 AC E0 00 05 32 78 00 05 B8 10 00 06 3D A8 00 00 00 54 00 05 32 78 00 05 B8 10 00 06 3D A8 00 06 C3 40 00 00 00 55 00 05 32 78 00 05 B8 10 00 06 3D A8 00 06 C3 40 00 00 00 56 00 05 32 78 00 05 B8 10 00 06 3D A8 00 06 C3 40 00 00 00 57 00 05 B8 10 00 06 3D A8 00 06 C3 40 00 07 48 74 00 00 00 58 00 05 B8 10 00 06 3D A8 00 06 C3 40 00 07 48 74 00 00 00 59 00 05 B8 10 00 06 3D A8 00 06 C3 40 00 07 48 74 00 00 00 5A 00 06 3D A8 00 06 C3 40 00 07 48 74 00 07 CE 0C 00 00 00 5B 00 06 3D A8 00 06 C3 40 00 07 48 74 00 07 CE 0C 00 00 00 5C 00 06 3D A8 00 06 C3 40 00 07 48 74 00 07 CE 0C 00 00 00 5D 00 06 C3 40 00 07 48 74 00 07 CE 0C 00 08 53 A4 00 00 00 5E 00 06 C3 40 00 07 48 74 00 07 CE 0C 00 08 53 A4 00 00 00 5F 00 06 C3 40 00 07 48 74 00 07 CE 0C 00 08 53 A4 00 00 00 60 00 07 48 74 00 07 CE 0C 00 08 53 A4 00 08 D9 3C 00 00 00 61 00 07 48 74 00 07 CE 0C 00 08 53 A4 00 08 D9 3C 00 00 00 62 00 07 48 74 00 07 CE 0C 00 08 53 A4 00 08 D9 3C 00 00 00 63 00 07 CE 0C 00 08 53 A4 00 08 D9 3C 00 09 5E D4 00 00 00 64 00 07 CE 0C 00 08 53 A4 00 08 D9 3C 00 09 5E D4 00 04 61 54");
		c.sendPacket(p, true);
	}
	
	public static void sendCashbackRatioInfo(LoginClient c) {
		Packet p = new Packet(OpcodeLogin.ENU_CASHBACK_RATIO_INFO_NOT);
		p.writeHexString("00 00 00 00 00 00 00 00");
		c.sendPacket(p, true);
	}
	
	public static void sendItemBuyInfo(LoginClient c) {
		Packet p = new Packet(OpcodeLogin.ENU_ITEM_BUY_INFO_NOT);
		
		/*
		p.write(0);
	
		Connection con = Database.getConnection();
		try {
			PreparedStatement ps = con.prepareStatement("SELECT StartID, EndID FROM shop");
			ResultSet rs = ps.executeQuery();
			if( rs.first() == true ) {
				do {
					p.writeInt( rs.getInt(0) );
					p.writeInt( rs.getInt(1) );
				}while( rs.next() == true );
			}
			
			rs.close();
			ps.close();
			con.close();
		}catch(Exception e) {
			e.printStackTrace(); return;
		}
		
		p.writeInt(0);
		*/

		// 하드코딩
		p.writeBool(false); // m_bBuyEnable
		p.writeInt(1); // 벡터 크기
		p.writeInt(0);
		p.writeInt(1730400); // 1630400
		p.writeInt(0);
		
		c.sendPacket(p, true);
	}
	
	public static void sendGuideBookList(LoginClient c) {
		Packet p = new Packet(OpcodeLogin.ENU_GUIDE_BOOK_LIST_ACK);
		p.writeHexString("00 00 00 01 00 00 00 00");
		c.sendPacket(p, true);
	}
	
	public static void sendClientPingConfig(LoginClient c) {
		Packet p = new Packet(OpcodeLogin.ENU_CLIENT_PING_CONFIG_ACK);
		p.writeHexString("00 00 0F A0 00 00 0F A0 00 00 0F A0 00 00 00 01 00 FF FF FF FF 00 00 00 00");
		c.sendPacket(p, false);
	}
}
