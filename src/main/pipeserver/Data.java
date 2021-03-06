/*
 * This file is part of the QuickServer library 
 * Copyright (C) 2003-2005 QuickServer.org
 *
 * Use, modification, copying and distribution of this software is subject to
 * the terms and conditions of the GNU Lesser General Public License. 
 * You should have received a copy of the GNU LGP License along with this 
 * library; if not, you can download a copy from <http://www.quickserver.org/>.
 *
 * For questions, suggestions, bug-reports, enhancement-requests etc.
 * visit http://www.quickserver.org
 *
 */

package pipeserver;

import org.quickserver.net.server.ClientData;
import org.quickserver.util.pool.PoolableObject;
import org.apache.commons.pool.BasePoolableObjectFactory; 
import org.apache.commons.pool.PoolableObjectFactory; 

import java.net.*;
import java.io.*;
import java.util.logging.*;

import org.quickserver.net.server.ClientHandler;

public class Data extends Thread implements ClientData, PoolableObject {
	private static Logger logger = Logger.getLogger(Data.class.getName());

	private static boolean logHex = false;
	private static boolean logText = false;

	private Socket socket;
	private ClientHandler handler;
	private BufferedInputStream bin;
	private BufferedOutputStream bout;


	private String remoteHost = "127.0.0.1";
	private int remotePort = 8080;

	private boolean init = false;	
	private boolean closed = false;
	private final Object lock = new Object();

	public Data(){
		super("DataThread");
		setDaemon(true);
		start();
	}

	public void setRemoteHost(String remoteHost) {
		this.remoteHost = remoteHost;
	}
	public String getRemoteHost() {
		return remoteHost;
	}

	public void setRemotePort(int remotePort) {
		this.remotePort = remotePort;
	}
	public int getRemotePort() {
		return remotePort;
	}

	public void setClosed(boolean flag) {
		closed = flag;
	}

	public void init(Socket socket, ClientHandler handler) {
		this.socket = socket;
		this.handler = handler;
		closed = false;
		try	{
			bin = new BufferedInputStream(socket.getInputStream());
			bout = new BufferedOutputStream(socket.getOutputStream());
			init = true;
			synchronized(lock) {
				lock.notify();
			}
		} catch(Exception e) {
			logger.warning("Error in init: "+e);
			handler.closeConnection();
			init = false;
			closed = true;
		}
	}

	public void preclean() {
		try	{
			if(bin!=null) bin.close();
			if(bout!=null) bout.close();
			if(socket!=null) socket.close();
		} catch(Exception e) {
			logger.fine("Error in preclean: "+e);
		}
	}

	public void run() {
		byte data[] = null;
		while(true) {
			try	{
				if(init==false) {
					synchronized(lock) {
						lock.wait();
					}
					continue;
				}
				
				data = readInputStream(bin);
				if(data==null) {
					init = false;
					logger.fine("Connection lost from remote pipe");
					handler.closeConnection();
				} else {

					if(logText) {
						logger.fine("S:Text: "+new String(data));
					}
					if(logHex) {
						logger.fine("S:Hex : "+hexencode(data));
					}
					handler.sendClientBinary(data);
				}
			} catch(Exception e) {
				init = false;
				if(closed==false) {
					logger.warning("Error in data thread : "+e);
				} else {
					logger.fine("Error after connection was closed in data thread : "+e);
				}
				//e.printStackTrace();
			}
		}//end of while
	}

	public void sendData(byte data[]) throws IOException {
		if(init==false)
			throw new IOException("Data is not yet init!");
		if(logText) {
			logger.fine("C:Text: "+new String(data));
		}
		if(logHex) {
			logger.fine("C:Hex : "+hexencode(data));
		}

		try	{
			bout.write(data, 0, data.length);
			bout.flush();
		} catch(Exception e) {
			if(closed==false) {
				logger.warning("Error sending data : "+e);
				throw new IOException(e.getMessage());
			} else {
				logger.fine("Error after connection was closed : sending data : "+e);
			}
		}		
	}

	public void clean() {
		socket = null;
		init = false;
		handler = null;
		bin = null;
		bout = null;
		remoteHost = "127.0.0.1";
		remotePort = 8080;
	}

	public boolean isPoolable() {
		return true;
	}

	public PoolableObjectFactory getPoolableObjectFactory() {
		return  new BasePoolableObjectFactory() {
			public Object makeObject() { 
				return new Data();
			} 
			public void passivateObject(Object obj) {
				Data ed = (Data)obj;
				ed.clean();
			} 
			public void destroyObject(Object obj) {
				if(obj==null) return;
				passivateObject(obj);
				obj = null;
			}
			public boolean validateObject(Object obj) {
				if(obj==null) 
					return false;
				else
					return true;
			}
		};
	}

	//-- helper methods --
	protected static byte[] readInputStream(InputStream _in) throws IOException {
		byte data[] = null;
		if(_in==null)
			throw new IOException("InputStream can't be null!");
		
		int s = _in.read();
		if(s==-1) {
			return null; //Connection lost
		}
		int alength = _in.available();
		if(alength > 0) {
			data = new byte[alength+1];	
			data[0] = (byte) s;
			int len = _in.read(data, 1, alength);
			if(len < alength) {
				data = copyOf(data, len+1);
			}
		} else {
			data = new byte[1];
			data[0] = (byte) s;
		}
		return data;
	}

	private static byte[] copyOf(byte data[], int len) {
		byte newdate[] = new byte[len];
		System.arraycopy(data, 0, newdate, 0, len);
		return newdate;
	}

	public static String hexencode(byte[] rawData) {
		StringBuilder hexText = new StringBuilder();
		String initialHex = null;
		int initHexLength = 0;

		for (int i = 0; i < rawData.length; i++) {
			int positiveValue = rawData[i] & 0x000000FF;
			initialHex = Integer.toHexString(positiveValue);
			initHexLength = initialHex.length();
			while (initHexLength++ < 2) {
				hexText.append("0");
			}
			hexText.append(initialHex);
		}
		return hexText.toString();
	}
}
