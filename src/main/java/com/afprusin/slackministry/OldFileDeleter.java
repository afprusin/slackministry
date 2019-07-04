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
	private static final int API_HAMMER_WAIT_SECONDS = 2;

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
		final long oneMonthAgo = getUnixTimeInSeconds() - (MONTHS_BACK_TO_SAVE * SECONDS_IN_MONTH);
		final List<File> files = getFilesBeforeDate(oneMonthAgo, authToken);

		files.forEach(file -> attemptToDeleteFile(file, authToken));
	}

	private List<File> getFilesBeforeDate(long date, String authToken) {
		List<File> files = new ArrayList<>();

		// TODO: This is supposedly not the correct way to paginate through results from a ListFiles call
		//  but this library does not seem to provide a URL to the next page (or a response metadata object)
		try {
			FilesListResponse response;
			int page = 1;
			Integer totalPages = null;
			do {
				response = slack.methods().filesList(
						getRequestForFilesBeforeDate(date, authToken, page));

				if (response == null || response.getFiles() == null || response.getFiles().isEmpty()) {
					break;
				}
				if (totalPages == null) {
					totalPages = response.getPaging().getPages();
					System.out.println("Found " + response.getPaging().getTotal() + " files to delete");
				}

				files.addAll(response.getFiles());
				page++;
			} while (page <= totalPages);
		} catch (IOException | SlackApiException e) {
			e.printStackTrace();
		}

		return files;
	}

	private FilesListRequest getRequestForFilesBeforeDate(long date, String authToken, int page) {
		// TODO: Library does not provide a way to set 'show files hidden by limit', for Slack's 'tombstoned' files
		//  when accounts go over their storage limit, the earliest files are hidden from API calls without this flag
		return FilesListRequest.builder()
				.tsTo(String.valueOf(date))
				.token(authToken)
				.page(page)
				.build();
	}


	private void attemptToDeleteFile(File toDelete, String authToken) {
		System.out.println("Attempting to delete: " + toDelete.getId() + " : " + toDelete.getName());
		FilesDeleteResponse deleteResponse = null;
		try {
			deleteResponse = slack.methods().filesDelete(FilesDeleteRequest.builder()
					.token(authToken)
					.file(toDelete.getId())
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
			TimeUnit.SECONDS.sleep(API_HAMMER_WAIT_SECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private long getUnixTimeInSeconds() {
		return (System.currentTimeMillis() / 1000L);
	}
}
