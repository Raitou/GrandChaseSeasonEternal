package server;

import java.net.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

import client.*;
import game.Channel;
import msg.KFriendInfo;
import util.Database;
import util.Ini;

public class Server extends Thread {
	public final static int LOGIN_SERVER = 1;
	public final static int GAME_SERVER = 2;
	public final static int MSG_SERVER = 3;
	public int server_type = 0;
	
	public int serverIndex = -1;
	public String serverName = "";
	public int serverPort = 0;
	ServerSocket ss = null;
	
	public Vector<Client> clients = null;
	
	public PingPong pingpong = null;
	
	// 게임서버만 사용하는 것
	// ----------------------
	public Vector<String> serverMessage = null;
	public int serverTypeFlag = 0; 
	public HashMap<Integer, Channel> serverCh = null;
	public String TCPRelayIP = "";
	public int TCPRelayPort = 0;
	public String UDPRelayIP = "";
	public int UDPRelayPort = 0;
	// ----------------------
	
	// 메신저서버만 사용하는 것
	// ----------------------
	public HashMap<Integer, KFriendInfo> FriendList = null;
	// ----------------------
	
	public Server(int port, int type, int srvidx) {
		server_type = type;
		serverIndex = srvidx;
		
		switch( server_type ) {
		case 1:
			serverName = "로그인서버";
			break;
		case 2:
			// 임시로 게임서버라고 하고, 포트를 DB에서 가져온다
			serverName = "게임서버";
			port = setGameServer();
			break;
		case 3:
			serverName = "메신저서버";
			break;
		}
		
		Main.printmsg("서버 생성 - " + serverName + " : " + port);
		
		serverPort = port;		
		clients = new Vector<Client>();
		
		pingpong = new PingPong(this);
		pingpong.start();
	}
	
	public int setGameServer() {
		/*
		serverName = Ini.getIni("game.server" + serverIndex + ".name");
		
		serverTypeFlag = Integer.parseInt( Ini.getIni("game.server" + serverIndex + ".typeflag") );
		
		ServerFunc.getServerMessage( this );
		
		TCPRelayIP = Ini.getIni("game.server" + serverIndex + ".tcprelay.ip");
		TCPRelayPort = Integer.parseInt( Ini.getIni("game.server" + serverIndex + ".tcprelay.port") );
		UDPRelayIP = Ini.getIni("game.server" + serverIndex + ".udprelay.ip");
		UDPRelayPort = Integer.parseInt( Ini.getIni("game.server" + serverIndex + ".udprelay.port") );
		*/
		
		boolean isFail = false;
		
		int ServerID = Integer.parseInt( Ini.getIni("game.server" + serverIndex + ".id") );
		int ServerPort = -1;
		
		Connection con = Database.getConnection();
 		PreparedStatement ps = null;
 		ResultSet rs = null;
 		try {
 			ps = con.prepareStatement("SELECT * FROM `gameserver` WHERE `ID` = ?");
 			ps.setInt(1, ServerID);
 			rs = ps.executeQuery();
 			if( rs.first() == true ) {
 				serverName = rs.getString("ServerName");
 				serverTypeFlag = rs.getInt("ServerTypeFlag");
 				ServerPort = rs.getInt("Port");
 			} else {
 				isFail = true; // DB에 서버 정보가 없다..?
 			}
 		}catch(Exception e) {
 			e.printStackTrace();
 			isFail = true; // 오류
 		} finally {
 			Database.close(con, ps, rs);
 		}
 		
		serverMessage = new Vector<String>();
		serverMessage.clear(); // 서버 접속시 메시지 나중에 구현하삼
		
		TCPRelayIP = Ini.getIni("game.tcprelay.ip");
		TCPRelayPort = Integer.parseInt( Ini.getIni("game.tcprelay.port") );
		UDPRelayIP = Ini.getIni("game.udprelay.ip");
		UDPRelayPort = Integer.parseInt( Ini.getIni("game.udprelay.port") );
		
		serverCh = new HashMap<Integer, Channel>();
		serverCh.put(1, new Channel(this));
		serverCh.put(6, new Channel(this));
		
		if( isFail )
			return -1;
		else
			return ServerPort;
	}
	
	@Override
	public void run() {
		Main.printmsg("서버 쓰레드 시작 - " + serverName + " : " + serverPort);
		
		// 포트가 -1이면 어디선가 실패했기 때문이다.
		if( serverPort == -1 ) {
			Main.printmsg("서버 시작이 취소되었습니다. - " + serverName + " : " + serverPort + " (인덱스: " + serverIndex + ")", 0);
			return;
		}
		
		try {
			ss = new ServerSocket(serverPort);
			Socket temp_socket = null;
			
			while( true ) {
				temp_socket = ss.accept();
				
				Client c = null;
				
				if( 1 == server_type ) {
					c = new LoginClient(temp_socket, this);
				} else if( 2 == server_type ) {
					c = new GameClient(temp_socket, this);
				} else if( 3 == server_type ) {
					c = new MsgClient(temp_socket, this);
				}
				
				clients.add(c);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
}
