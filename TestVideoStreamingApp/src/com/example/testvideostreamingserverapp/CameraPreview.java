package com.example.testvideostreamingserverapp;

import java.io.IOException;

import android.content.Context;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;

public class CameraPreview extends SurfaceView implements Callback {

	private SurfaceHolder surfaceHolder = null;
	private Camera camera = null;
	private boolean preview = true;
	
	public CameraPreview(Context context, Camera camera) {
		super(context);
		this.camera = camera;
		surfaceHolder = getHolder();
		surfaceHolder.addCallback(this);
		// deprecated setting, but required on Android versions prior to 3.0
		surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
		if( preview && surfaceHolder.getSurface() != null){
				try {
					camera.stopPreview();
				} catch (Exception ex) {
					// ignore: tried to stop a non-existent preview
				}

				// set preview size and make any resize, rotate or
				// reformatting changes here

				// start preview with new settings
				startPreview(holder);
		}
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		if(preview){
			startPreview(holder);
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		// TODO Auto-generated method stub

	}
	
	public void stopPreview(){
		camera.stopPreview();
		try{
			camera.setPreviewDisplay(null);
		}catch(Exception ex){
			ex.printStackTrace();
		}
		preview = false;
	}
	
	private void startPreview(final SurfaceHolder holder){
		(new Thread(){
			public void run() {
		try{
			camera.setPreviewDisplay(holder);
			camera.startPreview();
		}catch(Exception ex){
			Log.d(VIEW_LOG_TAG, "Error creating preview: "+ex.getMessage());
		}
			};
		}).start();
	}

}
