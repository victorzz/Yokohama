package org.mastor.turtle;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import android.app.Activity;
import android.content.Context;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.WebSettings.LayoutAlgorithm;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.view.View;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;


/**
 * Created on 5/21/2015
 * @author Alex Tam
 *
 */
public class WebViewActivity extends Activity{
	private WebView wv_main = null;
	//MAP - 存放要显示的图片信息
	private  ConcurrentHashMap<String, String> map = new  ConcurrentHashMap<String, String>();
	//图片文件夹
	private String rootPath = Environment.getExternalStorageDirectory().getAbsolutePath() 
			+ MainActivity.SEPERATOR + "WebViewDemo";
	
	private DAOHelper helper;
	//存放图片下载器信息
	private List<String> taskArray = new ArrayList<String>();
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
		/*
		if (VERSION.SDK_INT >= 19) {
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
		}
		*/
        View decorView = getWindow().getDecorView();

		// Hide both the navigation bar and the status bar.
		// SYSTEM_UI_FLAG_FULLSCREEN is only available on Android 4.1 and higher, but as
		// a general rule, you should design your app to hide the status bar whenever you
		// hide the navigation bar.
		int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
		decorView.setSystemUiVisibility(uiOptions);

		setContentView(R.layout.webview_act_main);
		//数据库操作类
		helper = new DAOHelper(WebViewActivity.this);

		//if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			//if (0 != (getApplicationInfo().flags &= ApplicationInfo.FLAG_DEBUGGABLE)) {
			//}
		//}

		start();
	}

	private void start() {
		wv_main = (WebView) findViewById(R.id.wv_main);

		wv_main.setWebChromeClient(new WebChromeClient() {
			public void onConsoleMessage(String message, int lineNumber, String sourceID) {
				Log.d("MyApplication", message + " -- From line "
						+ lineNumber + " of "
						+ sourceID);
			}
		});

		wv_main.setWebContentsDebuggingEnabled(true);

		//wv_main.getSettings().setJavaScriptEnabled(true);
		wv_main.setWebViewClient(new WebViewClient());
		// 单列显示
		wv_main.getSettings().setLayoutAlgorithm(LayoutAlgorithm.SINGLE_COLUMN);

		WebSettings webSettings = wv_main.getSettings();
		//enable JavaScript in webview
		webSettings.setJavaScriptEnabled(true);
		//Enable and setup JS localStorage
		webSettings.setDomStorageEnabled(true);
		//those two lines seem necessary to keep data that were stored even if the app was killed.
		webSettings.setDatabaseEnabled(true);
		webSettings.setDatabasePath("/data/data/" + wv_main.getContext().getPackageName() + "/databases/");

		wv_main.addJavascriptInterface(new JavascriptInterface(WebViewActivity.this), "mylistner");
		wv_main.addJavascriptInterface(new LocalStorageJavaScriptInterface(getApplicationContext()), "LocalStorage");

		// 为了模拟向服务器请求数据,加载HTML, 我已提前写好一份,放在本地直接加载
		wv_main.loadUrl("http://mastor.mooo.com:3000/menu/");
	}

	public void onBackPressed(){
		if(wv_main !=null){
			wv_main.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ESCAPE));
		}
	}

	private class JavascriptInterface 
	{
		private Context context;

		public JavascriptInterface(Context context) 
		{
			this.context = context;
		}

		//该方法被回调替换页面中的默认图片
		//@android.webkit.JavascriptInterface
		public String replaceimg(String imgPosition , String imgUrl, String imgTagId)
		{
			if(!map.containsKey(imgUrl))
			{	//如果中介存储器MAP中存在该图片信息,就直接使用,不再去数据库查询
				String imgPath = helper.find(imgUrl);
				if(imgPath != null && new File(imgPath).exists())
				{
					map.put(imgUrl, imgPath);
					return imgPath;
				}
				else
				{	
					
					if(taskArray.indexOf(imgUrl) < 0)
					{	// 当图片链接不存在数据库中,同时也没有正在下载该链接的任务时, 就添加新的下载任务
						// 下载任务完成会自动替换
						taskArray.add(imgUrl);
						DownLoadTask task = new DownLoadTask(imgTagId, imgPosition, imgUrl);
						task.execute();
					}
					// 为了模拟默认图片的加载进度, 在这里返回另一张不一样的默认图片,
					// 具体应用中,可以根据需求将该处改为某些百分比之类的图片
					return "file:///android_asset/test.jpg";
				}
				
			}
			else
			{
				return map.get(imgUrl);
			}
		}
		
		
	}
	
	//图片下载器
	private class DownLoadTask extends AsyncTask<Void, Void, String>
	{
		String imageId; 		//标签id
		String imagePosition;	//图片数组位置标记
		String imgUrl;			//图片网络链接
		
		public DownLoadTask(String imageId, String imagePosition, String imgUrl)
		{
			this.imageId = imageId;
			this.imagePosition = imagePosition;
			this.imgUrl = imgUrl;
		}

		@Override
		protected String doInBackground(Void... params) 
		{
			try 
			{
				// 下载图片
				URL url = new URL(imgUrl);
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				conn.setConnectTimeout(20 * 1000);  
				conn.setReadTimeout(20 * 1000);
		        conn.setRequestMethod("GET");  
		        conn.connect();
		        InputStream in = conn.getInputStream();
		        
		        byte[] myByte = readStream(in);
		        //压缩存储,有需要可以将bitmap放入别的缓存中,另作他用, 比如点击图片放大等等
		        Bitmap bitmap = BitmapFactory.decodeByteArray(myByte, 0, myByte.length);
		        
		        String fileName = Long.toString(System.currentTimeMillis()) + ".jpg";
		        File imgFile = new File(rootPath + MainActivity.SEPERATOR +fileName);
		        
		        BufferedOutputStream bos 
		        	= new BufferedOutputStream(new FileOutputStream(imgFile));  
		        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, bos); 
		        
		        bos.flush();  
		        bos.close();
		        
		        return imgFile.getAbsolutePath();
		        
			} 
			catch (Exception e) 
			{
				e.printStackTrace();
			}
			return null;
		}
		
		@Override
		protected void onPostExecute(String imgPath)
		{
			super.onPostExecute(imgPath);
			if(imgPath != null)
			{
				//对页面调用js方法, 将默认图片替换成下载后的图片
				String url = 
						"javascript:(function(){" 
						+ "var img = document.getElementById(\""
						+ imageId
						+ "\");"
						+ "if(img !== null){"
						+ "img.src = \""
						+ imgPath
						+ "\"; }"
						+ "})()";
						
				wv_main.loadUrl(url);
				// 将将图片信息缓存进中介存储器
				map.put(imgUrl, imgPath);
				// 将图片信息缓存进数据库
				helper.save(imgUrl, imgPath);
			}
			else
			{
				Log.e("WebViewActivity error", "DownLoadTask has a invalid imgPath...");
			}
			
		}
		
	}
	
	private byte[] readStream(InputStream inStream) throws Exception
	{  
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();  
        byte[] buffer = new byte[2048];  
        int len = 0;  
        while( (len=inStream.read(buffer)) != -1){  
            outStream.write(buffer, 0, len);  
        }  
        outStream.close();  
        inStream.close();  
        return outStream.toByteArray();  
    }


	private class LocalStorageJavaScriptInterface {
		private Context mContext;
		private LocalStorage localStorageDBHelper;
		private SQLiteDatabase database;

		LocalStorageJavaScriptInterface(Context c) {
			mContext = c;
			localStorageDBHelper = LocalStorage.getInstance(mContext);
		}

		/**
		 * This method allows to get an item for the given key
		 * @param key : the key to look for in the local storage
		 * @return the item having the given key
		 */
		//@JavascriptInterface
		public String getItem(String key)
		{
			String value = null;
			if(key != null)
			{
				database = localStorageDBHelper.getReadableDatabase();
				Cursor cursor = database.query(LocalStorage.LOCALSTORAGE_TABLE_NAME,
						null,
						LocalStorage.LOCALSTORAGE_ID + " = ?",
						new String [] {key},null, null, null);
				if(cursor.moveToFirst())
				{
					value = cursor.getString(1);
				}
				cursor.close();
				database.close();
			}
			return value;
		}

		/**
		 * set the value for the given key, or create the set of datas if the key does not exist already.
		 * @param key
		 * @param value
		 */
		//@JavascriptInterface
		public void setItem(String key,String value)
		{
			if(key != null && value != null)
			{
				String oldValue = getItem(key);
				database = localStorageDBHelper.getWritableDatabase();
				ContentValues values = new ContentValues();
				values.put(LocalStorage.LOCALSTORAGE_ID, key);
				values.put(LocalStorage.LOCALSTORAGE_VALUE, value);
				if(oldValue != null)
				{
					database.update(LocalStorage.LOCALSTORAGE_TABLE_NAME, values, LocalStorage.LOCALSTORAGE_ID + "='" + key + "'", null);
				}
				else
				{
					database.insert(LocalStorage.LOCALSTORAGE_TABLE_NAME, null, values);
				}
				database.close();
			}
		}

		/**
		 * removes the item corresponding to the given key
		 * @param key
		 */
		//@JavascriptInterface
		public void removeItem(String key)
		{
			if(key != null)
			{
				database = localStorageDBHelper.getWritableDatabase();
				database.delete(LocalStorage.LOCALSTORAGE_TABLE_NAME, LocalStorage.LOCALSTORAGE_ID + "='" + key + "'", null);
				database.close();
			}
		}

		/**
		 * clears all the local storage.
		 */
		//@JavascriptInterface
		public void clear()
		{
			database = localStorageDBHelper.getWritableDatabase();
			database.delete(LocalStorage.LOCALSTORAGE_TABLE_NAME, null, null);
			database.close();
		}
	}
	
	
}
