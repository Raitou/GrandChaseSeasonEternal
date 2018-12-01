package util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;

public class Convert {
	public static int byteArrayToIntBig(byte[] bytes) {
		return (((bytes[0] & 0xff) << 24) |
				((bytes[1] & 0xff) << 16) |
				((bytes[2] & 0xff) << 8) |
				((bytes[3] & 0xff)));
	}

	public static int byteArrayToIntBig(byte[] bytes, int offset) {
		return (((bytes[offset+0] & 0xff) << 24) |
				((bytes[offset+1] & 0xff) << 16) |
				((bytes[offset+2] & 0xff) << 8) |
				((bytes[offset+3] & 0xff)));
	}
	
	public static int byteArrayToShortBig(byte[] bytes) {
		return (((bytes[0] & 0xff) << 8) |
				(bytes[1] & 0xff));
	}
	
	public static int byteArrayToShortBig(byte[] bytes, int offset) {
		return (((bytes[offset+0] & 0xff) << 8) |
				(bytes[offset+1] & 0xff));
	}
	
	public static int byteArrayToIntLittle(byte[] bytes) {
		return (((bytes[0] & 0xff)) |
				((bytes[1] & 0xff) << 8) |
				((bytes[2] & 0xff) << 16) |
				((bytes[3] & 0xff) << 24));
	}
	
	public static int byteArrayToIntLittle(byte[] bytes, int offset) {
		return (((bytes[offset+0] & 0xff)) |
				((bytes[offset+1] & 0xff) << 8) |
				((bytes[offset+2] & 0xff) << 16) |
				((bytes[offset+3] & 0xff) << 24));
	}
	
	public static int byteArrayToShortLittle(byte[] bytes) {
		return (((bytes[0] & 0xff)) |
				(bytes[1] & 0xff) << 8);
	}
	
	public static int byteArrayToShortLittle(byte[] bytes, int offset) {
		return (((bytes[offset+1] & 0xff)) |
				(bytes[offset+0] & 0xff) << 8);
	}
	
	public static byte[] IntToByteArrayLittle(int n) {
		ByteBuffer bb = ByteBuffer.allocate(4);
		bb.order(ByteOrder.LITTLE_ENDIAN);
		bb.putInt(n);
		return bb.array();
	}
	
	public static byte[] IntToByteArrayBig(int n) {
		ByteBuffer bb = ByteBuffer.allocate(4);
		bb.order(ByteOrder.BIG_ENDIAN);
		bb.putInt(n);
		return bb.array();
	}
	
	public static byte[] ShortToByteArrayLittle(short n) {
		ByteBuffer bb = ByteBuffer.allocate(2);
		bb.order(ByteOrder.LITTLE_ENDIAN);
		bb.putShort(n);
		return bb.array();
	}
	
	public static byte[] ShortToByteArrayBig(short n) {
		ByteBuffer bb = ByteBuffer.allocate(2);
		bb.order(ByteOrder.BIG_ENDIAN);
		bb.putShort(n);
		return bb.array();
	}
	
	public static byte[] hexToByteArray(String hex) { 
		if (hex == null || hex.length() == 0) { 
			return null; 
		} 
		
		hex = hex.replace(" ", "");
		hex = hex.trim();
		
		byte[] ba = new byte[hex.length() / 2]; 
		
		for (int i = 0; i < ba.length; i++) { 
			ba[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
		} 
		
		return ba; 
	}

	public static String byteArrayToHexString(byte[] bytes) {
		StringBuilder sb = new StringBuilder("");
		
		for(final byte b : bytes)
	        sb.append(String.format("%02x ", b&0xff));
		
		return sb.toString();
	}
	
	public static String MD5(String str){
		String MD5 = ""; 
		try{
			MessageDigest md = MessageDigest.getInstance("MD5"); 
			md.update(str.getBytes()); 
			byte byteData[] = md.digest();
			StringBuffer sb = new StringBuffer(); 
			for(int i = 0 ; i < byteData.length ; i++){
				sb.append(Integer.toString((byteData[i]&0xff) + 0x100, 16).substring(1));
			}
			MD5 = sb.toString();
		}catch(Exception e){
			e.printStackTrace(); 
			MD5 = null; 
		}
		return MD5;
	}

	// 127.0.0.1 --> 0x7F000001
	public static int IPToInt(String ipAddress) {
		String[] ipAddressInArray = ipAddress.split("\\.");
		long result = 0;
		for (int i = 0; i < ipAddressInArray.length; i++) {
		
			int power = 3 - i;
			int ip = Integer.parseInt(ipAddressInArray[i]);
			result += ip * Math.pow(256, power);
		
		}
		return (int)(result & 0xFFFFFFFF);
	}
}
