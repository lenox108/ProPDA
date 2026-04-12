package forpdateam.ru.forpda.ui.views.messagepanel;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.view.inputmethod.InputMethodManager;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.cardview.widget.CardView;
import android.view.Gravity;
import android.text.Editable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import forpdateam.ru.forpda.App;
import forpdateam.ru.forpda.R;
import forpdateam.ru.forpda.common.simple.SimpleTextWatcher;
import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem;
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder;
import forpdateam.ru.forpda.ui.views.CodeEditor;
import forpdateam.ru.forpda.ui.views.messagepanel.advanced.AdvancedPopup;
import forpdateam.ru.forpda.ui.views.messagepanel.attachments.AttachmentsPopup;
import io.reactivex.disposables.CompositeDisposable;

/**
 * Created by radiationx on 07.01.17.
 */

@SuppressLint("ViewConstructor")
public class MessagePanel extends CardView {
    private ImageButton advancedButton, attachmentsButton, sendButton, fullButton, hideButton, editPollButton, clearMessageButton;
    private TextView attachmentsCounter;
    private List<View.OnClickListener> advancedListeners = new ArrayList<>(), attachmentsListeners = new ArrayList<>(), sendListeners = new ArrayList<>();
    private CodeEditor messageField;
    private MessagePanelBehavior panelBehavior;
    private AdvancedPopup advancedPopup;
    private AttachmentsPopup attachmentsPopup;
    private ViewGroup fragmentContainer;
    private ProgressBar sendProgress;
    private ProgressBar formProgress;
    private ScrollView messageWrapper;
    private int lastHeight = 0;
    private HeightChangeListener heightChangeListener;
    private boolean fullForm = false;
    private CoordinatorLayout.LayoutParams params;
    private boolean isMonospace = true;
    private MainPreferencesHolder mainPreferencesHolder = App.get().Di().getMainPreferencesHolder();
    private CompositeDisposable disposables = new CompositeDisposable();

    public MessagePanel(Context context, ViewGroup fragmentContainer, ViewGroup targetContainer, boolean fullForm) {
        super(context);
        isMonospace = mainPreferencesHolder.getEditorMonospace();
        this.fragmentContainer = fragmentContainer;
        this.fullForm = fullForm;
        init();
        targetContainer.addView(this, targetContainer.getChildCount() - 1);
        onCreatePanel();
    }

    private void init() {
        inflate(getContext(), fullForm ? R.layout.message_panel_full : R.layout.message_panel_quick, this);
        setClickable(true);
        advancedButton = (ImageButton) findViewById(R.id.button_advanced_input);
        attachmentsButton = (ImageButton) findViewById(R.id.button_attachments);
        attachmentsCounter = findViewById(R.id.attachment_counter);
        sendButton = (ImageButton) findViewById(R.id.button_send);
        fullButton = (ImageButton) findViewById(R.id.button_full);
        hideButton = (ImageButton) findViewById(R.id.button_hide);
        editPollButton = (ImageButton) findViewById(R.id.button_edt_poll);
        messageField = (CodeEditor) findViewById(R.id.message_field);
        sendProgress = (ProgressBar) findViewById(R.id.send_progress);
        formProgress = (ProgressBar) findViewById(R.id.form_load_progress);
        messageWrapper = (ScrollView) findViewById(R.id.message_wrapper);

        messageField.attachToScrollView(messageWrapper);
        messageWrapper.setEnabled(true);
        messageWrapper.setVerticalFadingEdgeEnabled(true);
        messageWrapper.setFadingEdgeLength(App.px8);

        panelBehavior = new MessagePanelBehavior();
        params = new CoordinatorLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, fullForm ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT);
        //params.setBehavior(panelBehavior);
        params.gravity = Gravity.BOTTOM;
        // Снизу без отступа: иначе зазор над нижним меню приложения и «просвет» с текстом темы.
        if (!fullForm)
            params.setMargins(App.px8, App.px8, App.px8, 0);
        setLayoutParams(params);
        setRadius(fullForm ? 0 : App.px8);
        setPreventCornerOverlap(false);
        setCardBackgroundColor(App.getColorFromAttr(getContext(), R.attr.cards_background));
        setClipChildren(true);
        setClipToPadding(true);
        //На случай, когда добавляются несколько слушателей
        advancedButton.setOnClickListener(v -> {
            for (OnClickListener listener : advancedListeners)
                listener.onClick(v);
        });
        attachmentsButton.setOnClickListener(v -> {
            for (OnClickListener listener : attachmentsListeners)
                listener.onClick(v);
        });
        sendButton.setOnClickListener(v -> {
            for (OnClickListener listener : sendListeners)
                listener.onClick(v);
        });
        clearMessageButton = (ImageButton) findViewById(R.id.button_clear_message);
        if (clearMessageButton != null) {
            clearMessageButton.setOnClickListener(v -> clearMessage());
        }

        lastHeight = getHeight() + App.px16;
        addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (heightChangeListener == null) return;
            int newHeight = getHeight() + App.px16;
            if (newHeight != lastHeight) {
                lastHeight = newHeight;
                heightChangeListener.onChangedHeight(newHeight);
            }
        });

        messageField.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0) {
                    if (sendButton.getColorFilter() == null) {
                        sendButton.setColorFilter(App.getColorFromAttr(getContext(), R.attr.colorAccent));
                    }
                } else {
                    if (sendButton.getColorFilter() != null) {
                        sendButton.clearColorFilter();
                    }
                }
            }
        });
        messageField.setTypeface(isMonospace ? Typeface.MONOSPACE : Typeface.DEFAULT);
        disposables.add(
                mainPreferencesHolder
                        .observeEditorMonospace()
                        .subscribe(value -> {
                            isMonospace = value;
                            messageField.setTypeface(isMonospace ? Typeface.MONOSPACE : Typeface.DEFAULT);
                        })
        );
        // IME не обрабатываем здесь: enableEdgeToEdge + adjustResize + ручной padding давали двойной отступ
        // («панель уехала вверх, клавиатура внизу»). Отступы — в MainActivity (decor fits) + updateDimens.
    }

    public int getLastHeight() {
        return lastHeight;
    }

    public HeightChangeListener getHeightChangeListener() {
        return heightChangeListener;
    }

    public void disableBehavior() {
        params.setBehavior(null);
        setLayoutParams(params);
    }

    public void enableBehavior() {
        params.setBehavior(panelBehavior);
        setLayoutParams(params);
    }

    public ProgressBar getFormProgress() {
        return formProgress;
    }

    public void setProgressState(boolean state) {
        sendProgress.setVisibility(state ? VISIBLE : GONE);
        sendButton.setVisibility(state ? GONE : VISIBLE);
    }

    public void show() {
        this.setTranslationY(0);
    }

    public void setText(String text) {
        messageField.setText(text);
    }

    public boolean insertText(String text) {
        return insertText(text, null);
    }

    public int[] getSelectionRange() {
        int selectionStart = messageField.getSelectionStart();
        int selectionEnd = messageField.getSelectionEnd();
        if (selectionEnd < selectionStart && selectionEnd != -1) {
            int c = selectionStart;
            selectionStart = selectionEnd;
            selectionEnd = c;
        }
        int len = messageField.getText().length();
        // Без фокуса getSelectionStart() == -1 — insert() падает или не вставляет BBCode
        if (selectionStart < 0 || selectionStart > len) {
            selectionStart = len;
        }
        if (selectionEnd < 0) {
            selectionEnd = selectionStart;
        }
        if (selectionEnd > len) {
            selectionEnd = len;
        }
        return new int[]{selectionStart, selectionEnd};
    }

    public boolean insertText(String startText, String endText) {
        return insertText(startText, endText, true);
    }

    public boolean insertText(String startText, String endText, int selectionStart, int selectionEnd) {
        return insertText(startText, endText, selectionStart, selectionEnd, true);
    }

    public boolean insertText(String startText, String endText, boolean selectionInside) {
        int[] selectionRange = getSelectionRange();
        int selectionStart = selectionRange[0];
        int selectionEnd = selectionRange[1];
        return insertText(startText, endText, selectionStart, selectionEnd, selectionInside);
    }

    public boolean insertText(String startText, String endText, int selectionStart, int selectionEnd, boolean selectionInside) {
        show();
        messageField.requestFocus();
        Editable editable = messageField.getText();
        int len = editable.length();
        if (selectionStart < 0 || selectionStart > len) {
            selectionStart = len;
        }
        if (selectionEnd < 0) {
            selectionEnd = selectionStart;
        }
        if (selectionEnd > len) {
            selectionEnd = len;
        }
        if (selectionEnd < selectionStart) {
            int t = selectionStart;
            selectionStart = selectionEnd;
            selectionEnd = t;
        }
        if (endText != null && selectionStart != selectionEnd) {
            editable.insert(selectionStart, startText);
            editable.insert(selectionEnd + startText.length(), endText);
            return true;
        }
        editable.insert(selectionStart, startText);
        if (endText != null) {
            editable.insert(selectionStart + startText.length(), endText);
            if (selectionInside) {
                messageField.setSelection(selectionStart + startText.length());
            }
        }

        return false;
    }

    public String getSelectedText() {
        int[] selectionRange = getSelectionRange();
        int s = selectionRange[0];
        int e = selectionRange[1];
        String t = messageField.getText().toString();
        if (s < 0) {
            s = 0;
        }
        if (e > t.length()) {
            e = t.length();
        }
        if (s > e) {
            return "";
        }
        return t.substring(s, e);
    }

    public void deleteSelected() {
        int[] selectionRange = getSelectionRange();
        int s = selectionRange[0];
        int e = selectionRange[1];
        if (s >= 0 && e <= messageField.getText().length() && s < e) {
            messageField.getText().delete(s, e);
        }
    }

    public void updateAttachmentsCounter(int count) {
        attachmentsCounter.setText("" + count);
        attachmentsCounter.setVisibility(count > 0 ? VISIBLE : GONE);
    }

    public String getMessage() {
        return messageField.getText().toString();
    }

    public void clearMessage() {
        messageField.setText("");
    }

    public void clearAttachments() {
        attachmentsPopup.clearAttachments();
    }

    public List<AttachmentItem> getAttachments() {
        return attachmentsPopup.getAttachments();
    }

    private void onCreatePanel() {
        attachmentsPopup = new AttachmentsPopup(getContext(), this);
        advancedPopup = new AdvancedPopup(getContext(), this, fullForm);
    }

    public AttachmentsPopup getAttachmentsPopup() {
        return attachmentsPopup;
    }

    public void addAdvancedOnClickListener(View.OnClickListener listener) {
        advancedListeners.add(listener);
    }

    public void addAttachmentsOnClickListener(View.OnClickListener listener) {
        attachmentsListeners.add(listener);
    }

    public void addSendOnClickListener(View.OnClickListener listener) {
        sendListeners.add(listener);
    }

    public ImageButton getAdvancedButton() {
        return advancedButton;
    }

    public ImageButton getAttachmentsButton() {
        return attachmentsButton;
    }

    public ImageButton getSendButton() {
        return sendButton;
    }

    public ImageButton getFullButton() {
        return fullButton;
    }

    public ImageButton getHideButton() {
        return hideButton;
    }

    public ImageButton getEditPollButton() {
        return editPollButton;
    }

    public EditText getMessageField() {
        return messageField;
    }

    public void showKeyboard() {
        messageField.requestFocus();
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(messageField, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    /** Скрыть IME по полю редактора; вызывать до {@code setVisibility(GONE)} у панели, иначе токен окна может быть недействителен. */
    public void hideImeFromEditor() {
        messageField.clearFocus();
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(messageField.getWindowToken(), 0);
        }
    }

    public void setCanScrolling(boolean canScrolling) {
        panelBehavior.setCanScrolling(canScrolling);
    }

    public ViewGroup getFragmentContainer() {
        return fragmentContainer;
    }

    public interface HeightChangeListener {
        void onChangedHeight(int newHeight);
    }

    public void setHeightChangeListener(HeightChangeListener heightChangeListener) {
        this.heightChangeListener = heightChangeListener;
    }

    public boolean onBackPressed() {
        return advancedPopup == null || advancedPopup.onBackPressed();
    }

    public void onResume() {
        if (advancedPopup != null)
            advancedPopup.onResume();
    }

    public void onDestroy() {
        if (advancedPopup != null)
            advancedPopup.onDestroy();
        if (!disposables.isDisposed()) {
            disposables.dispose();
        }
    }

    public void onPause() {
        if (advancedPopup != null)
            advancedPopup.onPause();
    }

    public void hidePopupWindows() {
        if (advancedPopup != null)
            advancedPopup.hidePopupWindows();
    }
}
