package com.quailstreetsoftware.newsreader;

import java.util.HashMap;

import com.quailstreetsoftware.newsreader.common.NotificationEvent;
import com.quailstreetsoftware.newsreader.common.NotificationParameter;
import com.quailstreetsoftware.newsreader.common.NotificationParameter.ParameterEnum;
import com.quailstreetsoftware.newsreader.common.interfaces.EventListener;
import com.quailstreetsoftware.newsreader.model.ModelContainer;
import com.quailstreetsoftware.newsreader.system.EventBus;
import com.quailstreetsoftware.newsreader.system.SoundAnnoyer;
import com.quailstreetsoftware.newsreader.ui.UIComponents;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;

public class Main extends Application implements EventListener {

	private GridPane grid;
	private UIComponents ui;
	private ModelContainer mc;
	private Boolean debugMenuDisplayed = Boolean.FALSE;
	private Node debugLog;
	private SoundAnnoyer soundAnnoyer;

	@Override
	public void start(Stage primaryStage) {

		grid = new GridPane();
		EventBus eventBus = new EventBus();
		mc = ModelContainer.restore(eventBus);
		if(mc == null) {
			mc = new ModelContainer(eventBus);
		}
		ui = new UIComponents(eventBus, mc.getSubscriptions(), this);
		soundAnnoyer = new SoundAnnoyer(eventBus);
		
		eventBus.addListener(mc);
		eventBus.addListener(ui);
		eventBus.addListener(this);
		eventBus.addListener(soundAnnoyer);

		try {

			grid.setGridLinesVisible(false);
			ColumnConstraints navColumn = new ColumnConstraints();
			navColumn.setPercentWidth(25);
			ColumnConstraints contentColumn = new ColumnConstraints();
			contentColumn.setPercentWidth(75);
			grid.getColumnConstraints().addAll(navColumn, contentColumn);

			// this is goofy, do this better.
			Node[] components = ui.getComponents();
			for (int i = 0; i < components.length; i++) {
				if (i == 0) {
					grid.add(components[i], 0, i, 2, 1);
				} else if (i == 2) {
					grid.add(components[i], 0, i, 2, 2);
				} else {
					grid.add(components[i], 1, i);
				}
			}
			grid.add(ui.getNavigation(), 0, 1, 1, 2);
			debugLog = ui.getDebugMenu();

			Scene scene = new Scene(grid, 1000, 800);
			scene.getStylesheets().add(getClass().getResource("application.css").toExternalForm());
			primaryStage.setScene(scene);
			primaryStage.setTitle("NewsReader");
			primaryStage.setOnCloseRequest(new EventHandler<WindowEvent>() {
				@Override
				public void handle(WindowEvent event) {
					try {
						mc.saveSubscriptions();
						Platform.exit();
						stop();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			});
			primaryStage.getIcons().add(new Image(this.getClass().getClassLoader().getResourceAsStream("META-INF/images/icon.png")));
			primaryStage.show();	
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		launch(args);
	}

	@Override
	public void eventOccurred(final NotificationEvent event, 
			final HashMap<ParameterEnum, NotificationParameter> arguments) {

		switch (event) {
		case REFRESH_SUBSCRIPTION_UI:
			String subscriptionId = arguments.get(ParameterEnum.SUBSCRIPTION_ID).getStringValue();
			ui.update(mc.getStories(subscriptionId));
			mc.saveSubscriptions();
			break;
		case CHANGED_SELECTED_SOURCE:
			String selectedSubscriptionId = arguments.get(ParameterEnum.SUBSCRIPTION_ID).getStringValue();
			ui.update(mc.getStories(selectedSubscriptionId));
			break;
		case TOGGLE_DEBUG:
            if(debugMenuDisplayed) {
            	grid.getChildren().remove(debugLog);
            	debugMenuDisplayed = Boolean.FALSE;
            } else {
            	grid.add(debugLog, 0, 4, 2, 1);
            	debugMenuDisplayed = Boolean.TRUE;
            }
		default:
			break;
		}

	}

	@Override
	public Boolean interested(NotificationEvent event) {
		switch (event) {
			case REFRESH_SUBSCRIPTION_UI:
			case CHANGED_SELECTED_SOURCE:
			case TOGGLE_DEBUG:
				return Boolean.TRUE;
			default:
				return Boolean.FALSE;
		}

	}
}
