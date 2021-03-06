package com.quailstreetsoftware.newsreader.model;

import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.quailstreetsoftware.newsreader.system.EventBus;
import com.quailstreetsoftware.newsreader.common.NotificationEvent;
import com.quailstreetsoftware.newsreader.common.NotificationParameter;
import com.quailstreetsoftware.newsreader.common.NotificationParameter.ParameterEnum;
import com.quailstreetsoftware.newsreader.common.Utility;

public class Subscription implements Serializable {

	private static final long serialVersionUID = 4575598134385729855L;
	public static String URL = "url";
	public static String TITLE = "title";

	private String id;
	private transient EventBus eventBus;
	private URL url;
	private String title;
	private int unread;
	private Boolean valid;
	private HashMap<String, Article> stories;
	private List<String> deletedGuids;
	private transient String urlString;
	private transient CloseableHttpClient httpClient;
	private transient HttpGet httpGet;
	private transient DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

	public Subscription(final EventBus eventBus, final String passedTitle,
			final String passedUrl) {

		this.id = UUID.randomUUID().toString();
		this.deletedGuids = new ArrayList<String>();
		this.eventBus = eventBus;
		this.stories = new HashMap<String, Article>();
		this.unread = 0;
		this.httpClient = HttpClients.createDefault();
		this.valid = Boolean.TRUE;
		this.title = passedTitle;
		try {
			this.url = new URL(passedUrl);
		} catch (MalformedURLException e) {
			this.valid = Boolean.FALSE;
		}
		this.httpGet = new HttpGet(passedUrl);
		this.refresh();
	}

	public Subscription(String dummyTitle) {
		this.title = dummyTitle;
	}

	public void refresh() {

		if (factory == null) {
			DocumentBuilderFactory.newInstance();
		}

		this.eventBus.fireEvent(NotificationEvent.DEBUG_MESSAGE,
				ParameterEnum.DEBUG_MESSAGE, "Refreshing subscription " + this.title);

		Task<ArrayList<Article>> task = new Task<ArrayList<Article>>() {

			@Override
			protected ArrayList<Article> call() throws Exception {

				ResponseHandler<String> responseHandler = new ResponseHandler<String>() {
					public String handleResponse(final HttpResponse response)
							throws ClientProtocolException, IOException {
						int status = response.getStatusLine().getStatusCode();
						if (status >= 200 && status < 300) {
							HttpEntity entity = response.getEntity();
							return entity != null ? EntityUtils
									.toString(entity) : null;
						} else {
							throw new ClientProtocolException(
									"Unexpected response status: " + status);
						}
					}
				};
				ArrayList<Article> articles = new ArrayList<Article>();
				String threadName = Thread.currentThread().getName();
				Platform.runLater(new Runnable() {
					@Override
					public void run() {
						eventBus.fireEvent(NotificationEvent.DEBUG_MESSAGE, 
								Utility.getParameterMap(
										new NotificationParameter(ParameterEnum.DEBUG_MESSAGE, "Sending http request to " + url.toString()),
										new NotificationParameter(ParameterEnum.THREAD_NAME, threadName)));
					}
				});
				String result = httpClient.execute(httpGet, responseHandler);
				if (factory == null) {
					factory = DocumentBuilderFactory.newInstance();
				}
				DocumentBuilder builder = factory.newDocumentBuilder();
				InputSource is = new InputSource(new StringReader(result));
				Document document = builder.parse(is);
				NodeList nodes = document.getElementsByTagName("item");
				for (int i = 0; i < nodes.getLength(); i++) {
					Article article = new Article(nodes.item(i), id);
					if(article.isValid()) {
						articles.add(article);
					}
				}
				return articles;
			}
		};

		task.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED,
				new EventHandler<WorkerStateEvent>() {
					@Override
					public void handle(WorkerStateEvent t) {
						ArrayList<Article> tempArticles = task.getValue();
						Boolean foundNew = Boolean.FALSE;
						for (Article item : tempArticles) {
							if (!stories.containsValue(item) && !deletedGuids.contains(item.getGuid())) {
								stories.put(item.getGuid(), item);
								unread++;
								foundNew = Boolean.TRUE;
							}
						}
						if (foundNew) {
							eventBus.fireEvent(NotificationEvent.REFRESH_SUBSCRIPTION_UI,
									ParameterEnum.SUBSCRIPTION_ID, id);
						}
					}
				});
		Thread updaterThread = new Thread(task);
		updaterThread.setName("UpdaterThread_" + title);
		updaterThread.setDaemon(true);
		updaterThread.start();
	}

	public URL getURL() {
		return this.url;
	}

	public Boolean isValid() {
		return this.valid;
	}

	public String getTitle() {
		return this.title;
	}

	public String getId() {
		return this.id;
	}

	public String handleResponse(final HttpResponse response)
			throws ClientProtocolException, IOException {
		int status = response.getStatusLine().getStatusCode();
		if (status >= 200 && status < 300) {
			HttpEntity entity = response.getEntity();
			return entity != null ? EntityUtils.toString(entity) : null;
		} else {
			throw new ClientProtocolException("Unexpected response status: "
					+ status);
		}
	}

	public Collection<Article> getStories() {
		ArrayList<Article> output = new ArrayList<Article>(this.stories.values());
		Collections.sort(output);
		return output;
	}

	public void initialize(EventBus passedEventBus) {
		this.eventBus = passedEventBus;
		this.httpClient = HttpClients.createDefault();
		this.valid = Boolean.TRUE;
		this.httpGet = new HttpGet(this.url.toString());
		this.refresh();
	}

	public String getURLString() {
		return this.urlString;
	}

	public void deleteArticle(final String id) {
		this.stories.remove(id);
		this.deletedGuids.add(id);
	}
	
	@Override
	public String toString() {
		if(this.stories != null && this.stories.size() < this.unread) {
			this.unread = 0;
			for(Article a : this.stories.values()) {
				if(a.isRead()) {
					this.unread++;
				}
			}
		}
		return this.title + " (" + this.unread + ")";
	}

	public void markRead(String articleId) {
		if(this.stories.get(articleId).setRead()) {
			this.unread--;
		}
		eventBus.fireEvent(NotificationEvent.SUBSCRIPTION_CHANGED,
				Utility.getParameterMap(new NotificationParameter(ParameterEnum.SUBSCRIPTION, this)));
	}

}
