package com.afprusin.slackministry;

import com.github.seratch.jslack.Slack;
import com.github.seratch.jslack.api.methods.SlackApiException;
import com.github.seratch.jslack.api.methods.request.files.FilesDeleteRequest;
import com.github.seratch.jslack.api.methods.request.files.FilesListRequest;
import com.github.seratch.jslack.api.methods.response.files.FilesDeleteResponse;
import com.github.seratch.jslack.api.methods.response.files.FilesListResponse;
import com.github.seratch.jslack.api.model.File;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class OldFileDeleter {
	private static final int SECONDS_IN_MONTH = 2592000;
	private static final int MONTHS_BACK_TO_SAVE = 1;

	public static void main(String[] args) {
		new OldFileDeleter().doStuff();
	}

	private Slack slack;

	private OldFileDeleter() {
		slack = Slack.getInstance();
	}

	private void doStuff() {
		deleteFilesOlderThanOneMonth(getUserAuthToken());
	}

	private String getUserAuthToken() {
		System.out.println("Enter user auth token (looks like aaaa-11111111111-11111....):");
		String authToken;
		try(BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
			authToken = reader.readLine().trim();
		} catch (IOException e) {
			throw new IllegalStateException("Encountered IO error while reading password.", e);
		}

		return authToken;
	}

	private void deleteFilesOlderThanOneMonth(String authToken) {
		final int oneMonthAgo = getUnixSeconds() - (MONTHS_BACK_TO_SAVE * SECONDS_IN_MONTH);

		// This could definitely get stuck and be terrible
		List<File> files = getFilesBeforeDate(oneMonthAgo, authToken);
		while( ! files.isEmpty()) {
			files.forEach(file -> attemptFileDelete(file, authToken));
			files = getFilesBeforeDate(oneMonthAgo, authToken);
		}
	}

	private List<File> getFilesBeforeDate(int date, String authToken) {
		FilesListResponse response = null;
		try {
			response = slack.methods().filesList(FilesListRequest.builder()
					.tsTo(String.valueOf(date))
					.token(authToken)
					.build());
		} catch (IOException | SlackApiException e) {
			e.printStackTrace();
		}

		List<File> files = new ArrayList<>();
		if(response != null) {
			files = response.getFiles();
		}

		return files;
	}

	private void attemptFileDelete(File file, String authToken) {
		System.out.println("Attempting to delete: " + file.getName() + " : " + file.getId());
		FilesDeleteResponse deleteResponse = null;
		try {
			deleteResponse = slack.methods().filesDelete(FilesDeleteRequest.builder()
					.token(authToken)
					.file(file.getId())
					.build());
		} catch (IOException | SlackApiException e) {
			e.printStackTrace();
		}
		if(deleteResponse != null) {
			if(deleteResponse.isOk()) {
				System.out.println("All good!");
			}
			if(deleteResponse.getError() != null  &&  deleteResponse.getError().length() > 0) {
				System.out.println("Deletion error: " + deleteResponse.getError());
			}
			if(deleteResponse.getWarning() != null  &&  deleteResponse.getWarning().length() > 0) {
				System.out.println("Deletion warning: " + deleteResponse.getWarning());
			}
		}
		try {
			TimeUnit.SECONDS.sleep(2);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private int getUnixSeconds() {
		return (int) (System.currentTimeMillis() / 1000L);
	}
}
