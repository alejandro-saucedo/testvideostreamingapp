package com.example.testvideostreamingserverapp;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;



import android.hardware.Camera;
import android.media.MediaRecorder;
import android.media.MediaRecorder.OutputFormat;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.app.Activity;
import android.util.Log;
import android.view.Menu;
import android.widget.FrameLayout;

public class ServerActivity extends Activity {
	
	public static final String TAG = ServerActivity.class.getName();
	public static final int PORT = 5678;
	public static final int PACKET_SIZE = 188;
	public static final int HEADER_SIZE = PACKET_SIZE*3;
	public static final int DATA_BUFFER_SIZE = PACKET_SIZE*10 ;
	private static final String LOCALSOCKETADDRESS = ServerActivity.class.getName();
	
	private class Client{
		private Socket socket = null;
		private OutputStream os = null;
		
		public Client(Socket socket) throws IOException{
			this.socket = socket;
			os = socket.getOutputStream();
		}
	}
	
	private CameraPreview preview = null;
	private Camera camera = null;
	private MediaRecorder recorder = null;
	private FilePathProvider fpp = null;
	private boolean recording = false;
	private boolean mrPrepared = false;
	private boolean serverAlive = true;
	private Collection<Client> clients = null;
	
	private ServerSocket serverSocket = null;
	private Socket clientSocket = null;
	private byte[] header = null;
	
	
	

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_server);
		//file provider
		fpp = new FilePathProvider(TAG);
		//setup camera
		FrameLayout frameLayout = (FrameLayout) findViewById(R.id.framaLayout);
		camera = getCamera();
		preview = new CameraPreview(this, camera);
		frameLayout.addView(preview);
		clients = new LinkedList<ServerActivity.Client>();
		launchServer();
		launchLocalServerSocket();
	}
	
	
	@Override
	protected void onResume() {
		super.onResume();
//		if(!recording){
//			(new Thread(){
//				
//				@Override
//				public void run() {
//					try{
//						//wait some time for the camera surface holder to be created 
//						Thread.sleep(1000);
//					}catch(InterruptedException ex){
//						ex.printStackTrace();
//					}
//					LocalSocket localClientSocket = new LocalSocket();
//					try{
//						localClientSocket.connect(new LocalSocketAddress(LOCALSOCKETADDRESS));
//						startRecording(localClientSocket.getFileDescriptor());
//					}catch(IOException ex){
//						Log.e(TAG, "Error connecting to local server", ex);
//					}
//				}
//				
//			}).start();
//		}
	}
	
	@Override
	protected void onStop() {
		super.onStop();
		pauseRecording();
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		stopRecording();
	}
	
	public void launchServer(){
		( new Thread() {
			@Override
			public void run() {
				try {
					serverSocket = new ServerSocket(PORT);
					while (serverAlive) {
						//receive client socket
						clientSocket = serverSocket.accept();

						try{
							Client client = new Client(clientSocket);
							if(header != null){
								client.os.write(header);
							}
							clients.add(client);
						}catch(Exception ex){
							Log.e(TAG, "error adding new client", ex);
						}
						
						//start recording when the first client connects successfully
						try{
							if(!recording){
								//create a local socket and set it as the mediarecorder data source
								LocalSocket localClientSocket = new LocalSocket();
								localClientSocket.connect(new LocalSocketAddress(LOCALSOCKETADDRESS));
								startRecording( localClientSocket.getFileDescriptor() );
							}
						}catch(Exception ex){
							Log.e(TAG, "problem starting to record", ex);
						}
					}
				} catch (Exception ex) {
					Log.e(TAG, "Problem starting TCP-IP server", ex);
				}
			}
		}).start();;
	}
	
	private void launchLocalServerSocket(){
		(new Thread() {
			public void run() {
				try {
					LocalServerSocket localServerSocket = new LocalServerSocket(LOCALSOCKETADDRESS);
					
					while (true) {
						LocalSocket localClientSocket = localServerSocket.accept();
						InputStream is = null;
						
						try {
							is = localClientSocket.getInputStream();
							BufferedInputStream bis = new BufferedInputStream(is);
							byte[] buff = new byte[DATA_BUFFER_SIZE];
							int bc = 0;
							int offset = 0;
							
							
							//read header
							if(header == null){
								header = new byte[HEADER_SIZE];
								boolean headerRead = false;
								while(!headerRead && (bc = bis.read(header, offset, HEADER_SIZE-offset))>=0){
									bc+=offset;
									if(bc<HEADER_SIZE){
										offset = bc;
									}else{
										headerRead = true;
										offset = 0;
										for(Client client : clients){
											client.os.write(header, 0, header.length);
											client.os.flush();
										}
									}
								}
							}
							
							while (((bc = bis.read(buff, offset, DATA_BUFFER_SIZE-offset)) >= 0)) {
								
								bc += offset;
								
								if(bc > 0 && (bc%188)==0){
									offset = 0;
									for(Client client : clients){
										try{
											client.os.write(buff, 0, bc);
											client.os.flush();
										}catch(Throwable ex){
											Log.e(TAG, "problem writing to client "+client.socket.getInetAddress(), ex);
											clients.remove(client);
										}
									}
								}else{
									offset = bc;
								}
							
						}
						} catch (IOException ex) {
							Log.e(TAG, "Problem reading data from local socket", ex);
						}

					}
				} catch (Exception ex) {
					Log.e(TAG, "Problem starting local server", ex);
				}
			}
		}).start();
	}
	
	public void shutDownServer(){
		serverAlive = false;
		try{
			serverSocket.close();
		}catch(IOException ex){
			Log.e(TAG, "Error closing server", ex);
			ex.printStackTrace();
		}
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	public Camera getCamera(){
		if(camera == null){
			try {
				camera = Camera.open(0);
			} catch (Exception ex) {
				Log.e(TAG, "Problem accessing camera: " + ex.getMessage());
			}
		}
		return camera;
	}
	
	public MediaRecorder getMediaRecorder(){
		if(recorder == null){
			recorder = new MediaRecorder();
		}
		return recorder;
	}
	
	private void releaseMediaRecorder(){
		if(recorder != null){
			recorder.reset();
			recorder.release();
			recorder = null;
			camera.lock();
			mrPrepared = false;
		}
	}
	
	private void releaseCamera(){
		if(camera != null){
			camera.release();
			camera = null;
		}
	}
	
	public void startRecording(FileDescriptor fd){
		if(!mrPrepared){
			//videoFile = fpp.getOutputMediaFile(FilePathProvider.MEDIA_TYPE_VIDEO);
			mrPrepared = prepareVideoRecorder(fd);
		}
		if(mrPrepared){
			try{
				recorder.start();
				recording = true;

			}catch(Exception ex){
				Log.e(TAG, "Error at recorder.start()", ex);
				recording = false;
				mrPrepared = false;
				releaseMediaRecorder();
				releaseCamera();
			}
		}
	}
	
	public void pauseRecording(){
		if(recording){
			try{
				recorder.stop();
			}catch(Exception ex){
				Log.e(TAG, "Error at recorder.stop()", ex);
			}finally{
				recording = false;
			}
			
		}
	}
	
	public void stopRecording(){
		pauseRecording();
		releaseMediaRecorder();
		releaseCamera();
	}
	
	public boolean prepareVideoRecorder(FileDescriptor fd){
		boolean prepared = false;
		
		camera = getCamera();
		recorder = getMediaRecorder();
		// Step 1: Unlock and set camera to MediaRecorder		
		camera.unlock();
		recorder.setCamera(camera);
		
		// Step 2: Set sources
		//recorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
		recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);     

		// Step 3: Set a CamcorderProfile (requires API Level 8 or higher)
		//recorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_TIME_LAPSE_LOW));
		
		// Step 4: Set output file		
		recorder.setOutputFile(fd);
		recorder.setOutputFormat(8);
		//recorder.setVideoEncodingBitRate(90);
        //recorder.setVideoFrameRate(20);
        //recorder.setVideoSize(176,144);
		recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
		//recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
		
		
		
		// Step 5: Set the preview output
		recorder.setPreviewDisplay(preview.getHolder().getSurface());
		
		
		// Step 6: Prepare configured MediaRecorder
		try{
			recorder.prepare();
			prepared = true;
		}catch(IllegalStateException ex){
			Log.d(TAG, "IllegalStateException preparing MediaRecorder: " + ex.getMessage());
	        releaseMediaRecorder();
	        releaseCamera();
		}catch(IOException ex){
	        Log.d(TAG, "IOException preparing MediaRecorder: " + ex.getMessage());
	        ex.printStackTrace();
	        
	        releaseMediaRecorder();
	        releaseCamera();
		}
		
		return prepared;

	}
	

}
