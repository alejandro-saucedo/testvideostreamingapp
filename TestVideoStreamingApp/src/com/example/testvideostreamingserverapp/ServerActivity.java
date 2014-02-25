package com.example.testvideostreamingserverapp;

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
	public static final int DATA_BUFFER_SIZE = 1024*2 ;
	private static final String LOCALSOCKETADDRESS = ServerActivity.class.getName();
	
	private File videoFile = null;
	private CameraPreview preview = null;
	private Camera camera = null;
	private MediaRecorder recorder = null;
	private Handler handler = null;
	private FilePathProvider fpp = null;
	private Thread serverThread = null;
	private boolean recording = false;
	private boolean mrPrepared = false;
	private boolean serverAlive = true;
	private boolean dpLaunched = false;
	private byte[] data = null;
	private Collection<OutputStream> outStreams = null;
	
	private ServerSocket serverSocket = null;
	private Socket clientSocket = null;
	
	
	

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_server);
		//file provider
		fpp = new FilePathProvider(TAG);
		data = new byte[DATA_BUFFER_SIZE];
		//setup camera
		FrameLayout frameLayout = (FrameLayout) findViewById(R.id.framaLayout);
		camera = getCamera();
		preview = new CameraPreview(this, camera);
		frameLayout.addView(preview);
		outStreams = new ArrayList<OutputStream>(1);
		launchServer();
		launchLocalServerSocket();
	}
	
	
	@Override
	protected void onResume() {
		super.onResume();
		(new Thread() {
			public void run() {
				try{
					Thread.sleep(5000);
				}catch(InterruptedException ex){
					ex.printStackTrace();
				}
				//startRecording();
				if (!dpLaunched) {
					//launchDataProvider();
				}
			};
		}).start();
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
		//tearDownServer();
	}
	
	public void launchServer(){
		serverThread = new Thread() {
			@Override
			public void run() {
				try {
					serverSocket = new ServerSocket(PORT);
					while (serverAlive) {
						//receive client socket
						clientSocket = serverSocket.accept();
						if(!recording){
							//create a local socket be the data output of the recorder
							LocalSocket localClientSocket = new LocalSocket();
							localClientSocket.connect(new LocalSocketAddress(LOCALSOCKETADDRESS));
							startRecording( localClientSocket.getFileDescriptor() );
						}
//						try {
//							synchronized (outStreams) {
//								outStreams.add(clientSocket.getOutputStream());
//							}
//						} catch (IOException ex) {
//							Log.e(TAG, "error getting socket output stream", ex);
//							ex.printStackTrace();
//						}
						
					}
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		};
		serverThread.start();
	}
	
	private void launchLocalServerSocket(){
		(new Thread() {
			public void run() {
				try {
					LocalServerSocket localServerSocket = new LocalServerSocket(LOCALSOCKETADDRESS);
					
					while (true) {
						LocalSocket localClientSocket = localServerSocket.accept();
						InputStream is = null;
						OutputStream os = null;
						try {
							is = localClientSocket.getInputStream();
							byte[] buff = new byte[DATA_BUFFER_SIZE];
							int bc = 0;
							while ((bc = is.read(buff)) > 0) {
								if (os == null && clientSocket != null) {
									os = clientSocket.getOutputStream();
								}
								if (os != null) {
									os.write(buff, 0, bc);
									os.flush();
								}
							}
						} catch (IOException ex) {
							ex.printStackTrace();
						}

					}
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		}).start();
	}
	
	public void launchDataProvider(){
		Thread dpThread = new Thread() {
			@Override
			public void run() {
	
				InputStream is = null;
				try {

					while (serverAlive) {
						if (videoFile != null) {
							try {
								if (is == null) {
									is = new FileInputStream(videoFile);
								}
								int bytesRead = 0;
								while ((bytesRead = is.read(data)) > 0) {
									synchronized (outStreams) {
										for (OutputStream os : outStreams) {
											try {
												os.write(data, 0, bytesRead);
												os.flush();
											} catch (IOException ex) {
												Log.e(TAG,
														"error sending data to one output stream",
														ex);
											}
										}
									}
								}

							} catch (IOException ex) {
								Log.e(TAG, "Error in data provider", ex);
							}
						}
						if (serverAlive) {
							try {
								Thread.sleep(3000);
							} catch (InterruptedException ex) {
								ex.printStackTrace();
							}
						}
					}
				} catch (Exception ex) {
					ex.printStackTrace();
				}finally{
					dpLaunched = false;
				}
			}
		};
		dpLaunched = true;
		dpThread.start();
	}

	public void tearDownServer(){
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
