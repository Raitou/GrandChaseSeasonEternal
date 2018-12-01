package server;

import client.Client;

public class PingPong extends Thread {
	public Server server = null;
	
	public PingPong(Server server){
		this.server = server;
	}
	
	// 연결 상태 체크
	public boolean checkConnect(Client c) {
		try {
			if( c.s.isConnected() == false || c.s.isClosed() == true || c.s.isBound() == false  ){
				return false;
			}
			
			return true;
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return false;
	}
	
	// 아직 미구현
	public boolean checkTimeout(Client c) {
		// 검사 당시 하트비트가 0이면 한번은 새로고쳐준다
		if( c.heartbit == 0 )
			c.heartbit = System.currentTimeMillis();
		
		// 검사 당시 '하트비트+100초'이 현재 시간보다 작아지면...
		if( c.heartbit + 100000 < System.currentTimeMillis() )
			return true;
		
		return false;
	}
	
	public void remove(Client c) {
		Main.printmsg("클라이언트 객체 삭제");
		
		server.clients.remove(c);
		c = null;
	}
	
	@Override
	public void run() {
		while(true) {
			try {
				for(Client c : server.clients) {
					// 닫혔다고 표시?
					if( c.isClosed == true ) {
						remove(c);
						break;
					}
					
					// 소켓이 없나?
					if( c.s == null ) {
						remove(c);
						break;
					}
					
					// 연결 되어있나?
					if( checkConnect(c) == false ) {
						c.close();
						remove(c);
						break;
					}
					
					// 타임아웃됐나?
					if( checkTimeout(c) == true ) {
						c.close();
						remove(c);
						break;
					}
				}
				
				Thread.sleep(5000);
			}catch(Exception e) {
				e.printStackTrace();
			}
		}
	}
}
