package packet;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;

import util.Convert;

public class Packet {
	public byte[] buffer = null;
	private int pos = 0;
	
	public int OpCode = -1;
	
	private ByteArrayOutputStream baos = null;
	
	public Packet(byte[] packet) {
		buffer = new byte[ packet.length ];
		System.arraycopy(packet, 0, buffer, 0, packet.length);
	}
	
	public Packet(int op) {
		OpCode = op;
		baos = new ByteArrayOutputStream();
	}
	
	// ----- 디버그 ---
	public int getPos() { return pos; }
	
	// ----- WRITE -----
	public void write(byte[] b) {
        for (int x = 0; x < b.length; x++) {
            baos.write(b[x]);
        }
    }
	
	public void write(int b) {
        baos.write((byte) b);
    }
	
	public void writeBool(boolean b) {
        baos.write( b == true ? 1 : 0 );
    }
	
	public void skip(int b) {
		write(new byte[b]);
    }
	
	public void writeShort(short i) {
		baos.write((byte) ((i >>> 8) & 0xFF));
        baos.write((byte) (i & 0xFF));
    }
	
	public void writeShort(int i) {
		writeShort( (short) i );
    }
	
	public void writeInt(int i) {
		baos.write((byte) ((i >>> 24) & 0xFF));
		baos.write((byte) ((i >>> 16) & 0xFF));
		baos.write((byte) ((i >>> 8) & 0xFF));
		baos.write((byte) (i & 0xFF));
    }
	
	public void writeIntLE(int i) {
		baos.write((byte) (i & 0xFF));
		baos.write((byte) ((i >>> 8) & 0xFF));
		baos.write((byte) ((i >>> 16) & 0xFF));
		baos.write((byte) ((i >>> 24) & 0xFF));
    }
	
	public void writeLong(long i) {
		baos.write((byte) ((i >>> 56) & 0xFF));
		baos.write((byte) ((i >>> 48) & 0xFF));
		baos.write((byte) ((i >>> 40) & 0xFF));
		baos.write((byte) ((i >>> 32) & 0xFF));
		
		baos.write((byte) ((i >>> 24) & 0xFF));
		baos.write((byte) ((i >>> 16) & 0xFF));
		baos.write((byte) ((i >>> 8) & 0xFF));
		baos.write((byte) (i & 0xFF));
    }
	
	public void writeFloat(float i) {
		int floatValue =  Float.floatToIntBits(i);
		writeInt(floatValue);
	}
	
	public void writeString(String s) {
        write(s.getBytes(Charset.forName("ASCII")));
    }
	
	public void writeStringWithLength(String s) {
		if( s == null ) {
			writeInt(0);
			return;
		}
		
		if( s.length() == 0 ) {
			writeInt(0);
			return;
		}
		
		writeInt(s.length());
		writeString(s);
    }
	
	public void writeUnicodeString(String s) {
        write(s.getBytes(Charset.forName("UTF-16LE")));
    }
	
	public void writeUnicodeStringWithLength(String s) {
		if( s == null ) {
			writeInt(0);
			return;
		}
		
		if( s.length() == 0 ) {
			writeInt(0);
			return;
		}
		
		writeInt(s.length() * 2);
		writeUnicodeString(s);
    }
	
	public void writeHexString(String s) {
        write(Convert.hexToByteArray(s));
    }
	
	public void applyWrite() {
		if( baos.size() < 10 ) {
			write(new byte[] {0,0,0,0,0,0,0,0,0,0});
		}
		
		buffer = baos.toByteArray();
	}
	
	// ----- READ -----
	public void seek(long offset) {
        pos = (int) offset;
    }
	
	public int read() {
        return (buffer[pos++]) & 0xFF;
    }
	
	public byte readByte() {
        return (byte)read();
    }
	
	public boolean readBool() {
        return read() == 1 ? true : false;
    }
	
	public short readShort() {
        return (short) ((read() << 8) + read());
    }
	
	public int readInt() {
        return (read() << 24) + (read() << 16) + (read() << 8) + read();
    }
	
	public int readIntLE() {
        return read() + (read() << 8) + (read() << 16) + (read() << 24);
    }
	
	public long readLong() {
        return (read() << 56) + (read() << 48) + (read() << 40) + (read() << 32) +
        		(read() << 24) + (read() << 16) + (read() << 8) + read();
    }
	
	public float readFloat() {
		int intvalue = readInt();
		return Float.intBitsToFloat(intvalue);
	}
	
	public String readString(int n) {
		byte ret[] = new byte[n];
		for (int x = 0; x < n; x++) {
		    ret[x] = readByte();
		}
		return new String(ret, Charset.forName("ASCII"));
	}
	
	public String readStringWithLength() {
		int len = readInt();
		
		if( len == 0 )
			return "";
		
		return readString(len);
	}
	
	public String readUnicodeString(int n) {
		byte ret[] = new byte[n];
		for (int x = 0; x < n; x++) {
		    ret[x] = readByte();
		}
		return new String(ret, Charset.forName("UTF-16LE"));
	}
	
	public String readUnicodeStringWithLength() {
		int len = readInt();
		
		if( len == 0 )
			return "";
		
		return readUnicodeString(len);
	}
}
