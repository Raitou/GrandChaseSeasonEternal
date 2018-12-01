package server;

import java.util.Vector;

import util.Ini;

public class ServerFunc {
	public static void getServerMessage(Server s) {
		if( s.serverMessage == null )
			s.serverMessage = new Vector<String>();
		
		s.serverMessage.clear();
		
		int cnt = Integer.parseInt( Ini.getIni("game.server" + s.serverIndex + ".msg.count") );
		
		for( int i=1; i<=cnt; i++ ) {
			String msg = Ini.getIni( "game.server" + s.serverIndex + ".msg" + i );
			s.serverMessage.add(msg);
		}
	}
}
