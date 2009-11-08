/**
 * New BSD License
 * http://www.opensource.org/licenses/bsd-license.php
 * Copyright (c) 2009, RaptorProject (http://code.google.com/p/raptor-chess-interface/)
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 * Neither the name of the RaptorProject nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package raptor.swt.chat;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

import raptor.Raptor;
import raptor.action.RaptorAction;
import raptor.action.SeparatorAction;
import raptor.action.RaptorAction.RaptorActionContainer;
import raptor.action.chat.PrependAction;
import raptor.action.chat.TellsMissedWhileIWasAwayAction;
import raptor.action.chat.ToggleScrollLock;
import raptor.chat.ChatEvent;
import raptor.chat.ChatLogger.ChatEventParseListener;
import raptor.connector.Connector;
import raptor.service.ActionService;
import raptor.service.ThreadService;
import raptor.swt.chat.controller.ToolBarItemKey;

public class ChatUtils {
	public static final String FORWARD_CHAR = " `1234567890-=qwertyuiop[]\\asdfghjkl;'zxcvbnm,./?><MNBVCXZ\":LKJHGFDSA|}{POIUYTREWQ+_)(*&^%$#@!~";
	private static final Log LOG = LogFactory.getLog(ChatUtils.class);

	public static void addActionsToToolbar(
			final ChatConsoleController controller,
			RaptorActionContainer container, ToolBar toolbar) {
		RaptorAction[] toolbarActions = ActionService.getInstance().getActions(
				container);

		for (RaptorAction action : toolbarActions) {
			ToolItem item = createToolItem(action, controller, toolbar);

			if (LOG.isDebugEnabled()) {
				LOG.debug("Added " + action + " to toolbar " + item);
			}
		}
		new ToolItem(toolbar, SWT.SEPARATOR);
	}

	/**
	 * Appends all of the previous chat events to the controller. This method
	 * executes asynchronously.
	 */
	public static void appendPreviousChatsToController(final ChatConsole console) {
		ThreadService.getInstance().run(new Runnable() {
			public void run() {
				console.getController().setSoundDisabled(true);
				console.getController().getConnector().getChatService()
						.getChatLogger().parseFile(
								new ChatEventParseListener() {

									public void onNewEventParsed(
											final ChatEvent event) {
										console.getDisplay().syncExec(
												new Runnable() {
													public void run() {
														try {
															if (!console
																	.isDisposed()) {
																console
																		.getController()
																		.onChatEvent(
																				event);
															}
														} catch (Throwable t) {
															console
																	.getController()
																	.getConnector()
																	.onError(
																			"appendPreviousChatsToController",
																			t);
														}
													}
												});
									}

									public void onParseCompleted() {
										console.getController()
												.setSoundDisabled(false);
									}
								});
			}
		});
	}

	/**
	 * Returns the character at the specified position in the StyledText.
	 */
	public static char charAt(StyledText text, int position) {
		return text.getContent().getTextRange(position, 1).charAt(0);
	}

	/**
	 * Removes tailing prompts form the text.
	 * 
	 * @param text
	 *            The text.
	 * @param connector
	 *            The connector to obtain the prompt from.
	 * @return The result.
	 */
	public static String filterTrailingPrompts(String text, Connector connector) {
		if (text.endsWith(connector.getPrompt())) {
			return text.substring(0,
					text.length() - connector.getPrompt().length()).trim();
		} else if (text.endsWith(connector.getPrompt() + " ")) {
			return text.substring(0,
					text.length() - connector.getPrompt().length() - 1).trim();
		}
		return text;
	}

	/**
	 * Returns null if the current position isn't quoted text, otherwise returns
	 * the text in quotes. Both single and double quotes are supported.
	 */
	public static String getQuotedText(StyledText text, int position) {
		try {
			int quoteStart = -1;
			int quoteEnd = -1;

			int currentPosition = position;
			char currentChar = charAt(text, currentPosition);

			if (currentChar == '\"' || currentChar == '\'') {
				quoteEnd = position;
				currentChar = charAt(text, --currentPosition);
			}

			while (currentChar != '\"' && currentChar != '\'') {
				if (currentChar == '\r' || currentChar == '\n') {
					return null;
				}
				currentChar = charAt(text, --currentPosition);
			}

			quoteStart = currentPosition;

			if (quoteEnd == -1) {
				currentPosition = position + 1;
				currentChar = charAt(text, currentPosition);

				while (currentChar != '\"' && currentChar != '\'') {
					if (currentChar == '\r' || currentChar == '\n') {
						return null;
					}
					currentChar = charAt(text, ++currentPosition);
				}

				quoteEnd = currentPosition;
			}
			return text.getText(quoteStart + 1, quoteEnd - 1);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Returns the url at the specified position, null if there is not one. This
	 * method handles ICS wrapping and will remove it and return just the url.
	 */
	public static String getUrl(StyledText text, int position) {
		String candidateWord = getWrappedWord(text, position);
		if (candidateWord != null
				&& (candidateWord.startsWith("http://") || candidateWord
						.startsWith("https://"))) {
			return candidateWord;
		} else if (candidateWord != null
				&& (candidateWord.endsWith(".com")
						|| candidateWord.endsWith(".org")
						|| candidateWord.endsWith(".edu") || candidateWord
						.startsWith("www.")) && !candidateWord.contains("@")) {
			if (candidateWord.endsWith(".") || candidateWord.endsWith(",")) {
				candidateWord = candidateWord.substring(0, candidateWord
						.length() - 1);
			}
			return "http://" + candidateWord;
		} else {
			return null;
		}
	}

	/**
	 * Returns the word at the specified position, null if there is not one.
	 */
	public static String getWord(StyledText text, int position) {
		try {
			int lineStart;
			int lineEnd;

			int currentPosition = position;
			char currentChar = charAt(text, currentPosition);

			while (currentPosition > 0 && !Character.isWhitespace(currentChar)) {
				currentChar = charAt(text, --currentPosition);
			}

			lineStart = currentPosition;

			currentPosition = position;
			currentChar = charAt(text, currentPosition);

			while (currentPosition < text.getCharCount()
					&& !Character.isWhitespace(currentChar)) {
				currentChar = charAt(text, ++currentPosition);
			}

			lineEnd = currentPosition;

			return trimDateStampFromWord(text.getText(lineStart + 1,
					lineEnd - 1));

		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Returns null if the current position isn't a wrapped word, otherwise
	 * returns the word with the ICS wrapping removed.
	 */
	public static String getWrappedWord(StyledText text, int position) {
		try {
			String result = null;
			int lineStart;
			int lineEnd;

			int currentPosition = position;
			char currentChar = charAt(text, currentPosition);

			// This method currently does'nt check backwards through all the
			// wraps.
			// This should probably be added in the future sometime so you can
			// click on
			// the second or third wrapped line of a link and it will work.
			while (currentPosition > 0 && !Character.isWhitespace(currentChar)) {
				currentChar = charAt(text, --currentPosition);
			}

			lineStart = currentPosition;

			currentPosition = position;
			currentChar = charAt(text, currentPosition);

			while (currentPosition < text.getCharCount() - 1
					&& !Character.isWhitespace(currentChar)) {
				currentChar = charAt(text, ++currentPosition);
			}
			lineEnd = currentPosition;
			if (lineEnd >= text.getCharCount() - 1) {
				result = text.getText(lineStart + 1, text.getCharCount() - 1);

			} else {
				result = text.getText(lineStart + 1, lineEnd - 1);

				// now check to see if its a wrap
				while (Character.isWhitespace(currentChar)
						&& currentPosition < text.getCharCount() - 1) {
					currentChar = charAt(text, ++currentPosition);
				}
				while (currentChar == '\\') {
					currentChar = charAt(text, ++currentPosition);
					while (Character.isWhitespace(currentChar)
							&& currentPosition < text.getCharCount() - 1) {
						currentChar = charAt(text, ++currentPosition);
					}

					lineStart = currentPosition - 1;
					while (!Character.isWhitespace(currentChar)
							&& currentPosition < text.getCharCount() - 1) {
						currentChar = charAt(text, ++currentPosition);
					}

					lineEnd = currentPosition;
					result += text.getText(lineStart + 1, lineEnd - 1);

					while (Character.isWhitespace(currentChar)
							&& currentPosition < text.getCharCount() - 1) {
						currentChar = charAt(text, ++currentPosition);
					}
				}
			}

			if (result != null) {
				return trimDateStampFromWord(result);
			}
			return result;

		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public static String trimDateStampFromWord(String word) {
		if (word.startsWith("[")) {
			int closingBrace = word.indexOf("]");
			if (closingBrace != -1) {
				return word.substring(closingBrace + 1);
			}
		}
		return word;
	}

	protected static ToolItem createToolItem(final RaptorAction action,
			final ChatConsoleController controller, ToolBar toolbar) {
		ToolItem result = null;
		if (action instanceof SeparatorAction) {
			result = new ToolItem(toolbar, SWT.SEPARATOR);
			return result;
		} else if (action instanceof ToggleScrollLock) {
			result = new ToolItem(toolbar, SWT.CHECK);
			result.setSelection(true);
			result.setToolTipText("Scroll lock enabled");
			controller.addToolItem(ToolBarItemKey.AUTO_SCROLL_BUTTON, result);
		} else if (action instanceof PrependAction) {
			result = new ToolItem(toolbar, SWT.CHECK);
			controller.addToolItem(ToolBarItemKey.PREPEND_TEXT_BUTTON, result);
			result.setSelection(true);
		} else if (action instanceof TellsMissedWhileIWasAwayAction) {
			result = new ToolItem(toolbar, SWT.PUSH);
			controller.addToolItem(ToolBarItemKey.AWAY_BUTTON, result);
			result.setEnabled(false);
		} else {
			result = new ToolItem(toolbar, SWT.PUSH);
		}

		if (result.getText() != null && StringUtils.isBlank(action.getIcon())) {
			result.setText(action.getName());
		} else if (StringUtils.isNotBlank(action.getIcon())) {
			result.setImage(Raptor.getInstance().getIcon(action.getIcon()));
		} else {
			Raptor.getInstance().alert(
					"There is no image or short name set for action "
							+ action.getName());
		}

		if (StringUtils.isNotBlank(action.getDescription())) {
			result.setToolTipText(action.getDescription());
		}

		result.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				RaptorAction loadedAction = ActionService.getInstance()
						.getAction(action.getName());
				loadedAction.setChatConsoleControllerSource(controller);
				loadedAction.run();
			}
		});
		return result;
	}

}
