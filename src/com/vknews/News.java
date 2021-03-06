/**
 * Contains logic related to news list processing.
 */
package com.vknews;

import java.io.IOException;
import java.util.ArrayList;

import org.apache.http.client.ClientProtocolException;
import org.json.JSONException;

import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Process News list activity.
 */
public class News extends ListActivity implements OnScrollListener {

	/**
	 * Message to create news list
	 */
	private static final int CREATE_LIST = 0;
	/**
	 * Message to update news list
	 */
	private static final int UPDATE_LIST = 1;

	/**
	 * Current number of news
	 */
	private int mCount = 0;
	/**
	 * News list adapter
	 */
	private VkNewsAdapter mAdapter;
	/**
	 * Long time operations handler.
	 */
	private ProgressDialog mProgress;
	/**
	 * Use synchronized getNews() for access
	 */
	private ArrayList<NewsItem> mNews;
	/**
	 * Handler of update and create list events
	 */
	private Handler mHandler = new ViewHandler();

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.news_list);

		new ListLoader().execute(); // initial loading
		getListView().setOnScrollListener(this);
		Button logout = (Button) findViewById(R.id.logout);
		logout.setOnClickListener(new ButtonListener());
		Button update = (Button) findViewById(R.id.update);
		update.setOnClickListener(new ButtonListener());
		
	}

	/**
	 * Process logout button click.
	 */
	class ButtonListener implements OnClickListener {
		/**
		 * Process logout button click.
		 */
		@Override
		public void onClick(View v) {
			switch(v.getId()){
			case R.id.logout:
				logout();
				break;
			case R.id.update:
				update();
			}
			
		}
		
		private void logout(){
			Intent i = new Intent(News.this, Authorization.class);
			i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			ApiHandler.logout();
			startActivity(i);
			finish();
		}
		
		private void update(){
			setNews(null);
			new ListLoader().execute(); // initial loading
		}
	}

	/**
	 * Add news to end of news list
	 */
	@Override
	public void onScroll(AbsListView view, int firstVisibleItem,
			int visibleItemCount, int totalItemCount) {

		if (((firstVisibleItem + visibleItemCount) >= totalItemCount)
				&& totalItemCount != 0) {
			new ListLoader().execute();
		}
	}

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState) {
	}

	/**
	 * Intended for debug purposes.
	 */
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		String selection = l.getItemAtPosition(position).toString();
		Toast.makeText(this, selection, Toast.LENGTH_LONG).show();
	}

	private synchronized ArrayList<NewsItem> getNews() {
		return mNews;
	}

	private synchronized void setNews(ArrayList<NewsItem> news) {
		mNews = news;
	}

	/**
	 * Loader of news list
	 */
	public class ListLoader extends AsyncTask<Void, Void, Void> {
		/**
		 * Dismiss progress dialog
		 */
		@Override
		protected void onPostExecute(Void result) {
			if (mProgress.isShowing()) {
				mProgress.dismiss();
			}
		}

		/**
		 * Show progress dialog
		 */
		@Override
		protected void onPreExecute() {
			if (mProgress == null || !mProgress.isShowing()) {
				mProgress = ProgressDialog.show(News.this,
						getString(R.string.get_data),
						getString(R.string.loading), true);
			}
		}

		/**
		 * Retrieve news from network
		 */
		@Override
		protected Void doInBackground(Void... params) {
			try {
				if (getNews() == null) {
					processNewsInitial();
				} else {
					processNews();
				}
			} catch (IOException e) {
				e.printStackTrace();
			} catch (JSONException e) {
				e.printStackTrace();
			}
			return null;
		}

		/**
		 * Retrieve initial news list
		 * 
		 * @throws ClientProtocolException
		 * @throws IOException
		 * @throws JSONException
		 */
		private synchronized void processNewsInitial()
				throws ClientProtocolException, IOException, JSONException {
			long lastTime = System.currentTimeMillis() / 1000;
			long startTime = lastTime - Utils.MONTH;
			setNews(ApiHandler.getData(lastTime, startTime));
			mHandler.sendEmptyMessage(CREATE_LIST);
		}

		/**
		 * Retrieve additional news when to scroll to the last news
		 * 
		 * @throws ClientProtocolException
		 * @throws IOException
		 * @throws JSONException
		 */
		private synchronized void processNews() throws ClientProtocolException,
				IOException, JSONException {

			long lastTime = mAdapter.getItem(mCount - 1).date;
			long startTime = lastTime - Utils.MONTH;

			setNews(ApiHandler.getData(lastTime, startTime));
			mHandler.sendEmptyMessage(UPDATE_LIST);
		}
	}

	/**
	 * News list Adapter
	 */
	private class VkNewsAdapter extends ArrayAdapter<NewsItem> {
		/**
		 * Constructor
		 * 
		 * @param context
		 * @param textViewResourceId
		 * @param news
		 */
		public VkNewsAdapter(Context context, int textViewResourceId,
				ArrayList<NewsItem> news) {
			super(context, textViewResourceId, news);
		}

		/**
		 * Draws news
		 */
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View row = convertView;
			if (row == null) {
				LayoutInflater inflater = getLayoutInflater();
				row = inflater.inflate(R.layout.news, parent, false);
			}

			NewsItem news = mAdapter.getItem(position);

			NewsItem.Profile profile = news.profile;
			TextView nameText = (TextView) row.findViewById(R.id.name);
			nameText.setText(profile.firstName + " " + profile.lastName);

			TextView newsText = (TextView) row.findViewById(R.id.news);
			newsText.setText(news.text);

			TextView timeAgo = (TextView) row.findViewById(R.id.time_ago);
			String date = Utils.formatTimeAgo(System.currentTimeMillis() / 1000
					- news.date);
			timeAgo.setText(date);

			ImageView icon = (ImageView) row.findViewById(R.id.photo);
			icon.setImageBitmap(profile.photo);

			return row;
		}
	}

	/**
	 * Create / update list message handler
	 */
	class ViewHandler extends Handler {

		/**
		 * Handle CREATE_LIST and UPDATE_LIST messages from {@link ListLoader}.
		 * Updates view.
		 */
		@Override
		public void handleMessage(Message msg) {
			if (msg.what == CREATE_LIST) {
				mAdapter = new VkNewsAdapter(News.this, R.layout.news,
						getNews());
				getListView().setAdapter(mAdapter);
			} else if (msg.what == UPDATE_LIST) {
				for (NewsItem n : getNews()) {
					mAdapter.add(n);
				}
			}
			mCount = mAdapter.getCount();
		}
	}

}