/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.pages.main.participants;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v7.widget.Toolbar;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import com.waz.api.IConversation;
import com.waz.api.Message;
import com.waz.api.NetworkMode;
import com.waz.api.OtrClient;
import com.waz.api.User;
import com.waz.api.UsersList;
import com.waz.api.Verification;
import com.waz.model.ConvId;
import com.waz.model.ConversationData;
import com.waz.model.IntegrationId;
import com.waz.model.ProviderId;
import com.waz.model.UserData;
import com.waz.model.UserId;
import com.waz.zclient.BaseActivity;
import com.waz.zclient.R;
import com.waz.zclient.common.controllers.ThemeController;
import com.waz.zclient.common.views.UserDetailsView;
import com.waz.zclient.controllers.accentcolor.AccentColorObserver;
import com.waz.zclient.controllers.globallayout.KeyboardVisibilityObserver;
import com.waz.zclient.conversation.ConversationController;
import com.waz.zclient.core.stores.connect.ConnectStoreObserver;
import com.waz.zclient.core.stores.connect.IConnectStore;
import com.waz.zclient.core.stores.network.NetworkAction;
import com.waz.zclient.core.stores.participants.ParticipantsStoreObserver;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.pages.main.conversation.controller.ConversationScreenControllerObserver;
import com.waz.zclient.participants.ParticipantsController;
import com.waz.zclient.ui.text.AccentColorEditText;
import com.waz.zclient.ui.utils.KeyboardUtils;
import com.waz.zclient.ui.utils.MathUtils;
import com.waz.zclient.utils.Callback;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.views.e2ee.ShieldView;

public class ParticipantHeaderFragment extends BaseFragment<ParticipantHeaderFragment.Container> implements KeyboardVisibilityObserver,
                                                                                                            ParticipantsStoreObserver,
                                                                                                            View.OnClickListener,
                                                                                                            ConversationScreenControllerObserver,
                                                                                                            ConnectStoreObserver,
                                                                                                            AccentColorObserver {
    public static final String TAG = ParticipantHeaderFragment.class.getName();

    private Toolbar toolbar;
    private TextView membersCountTextView;
    private UserDetailsView userDetailsView;
    private AccentColorEditText headerEditText;
    private TextView headerReadOnlyTextView;
    private View bottomBorder;
    private TextView penIcon;
    private ShieldView shieldView;

    private ConversationController convController;
    private ParticipantsController participantsController;

    public static ParticipantHeaderFragment newInstance() {
        return new ParticipantHeaderFragment();
    }

    @Override
    public Animation onCreateAnimation(int transit, boolean enter, int nextAnim) {
        final Fragment parent = getParentFragment();

        // Apply the workaround only if this is a child fragment, and the parent
        // is being removed.
        if (!enter && parent != null && parent.isRemoving()) {
            // This is a workaround for the bug where child fragments disappear when
            // the parent is removed (as all children are first removed from the parent)
            // See https://code.google.com/p/android/issues/detail?id=55228
            Animation doNothingAnim = new AlphaAnimation(1, 1);
            doNothingAnim.setDuration(ViewUtils.getNextAnimationDuration(parent));
            return doNothingAnim;
        } else {
            return super.onCreateAnimation(transit, enter, nextAnim);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, final ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_participants_header, container, false);

        convController = inject(ConversationController.class);
        participantsController = inject(ParticipantsController.class);

        toolbar = ViewUtils.getView(rootView, R.id.t__participants__toolbar);
        membersCountTextView = ViewUtils.getView(rootView, R.id.ttv__participants__sub_header);
        userDetailsView = ViewUtils.getView(rootView, R.id.udv__participants__user_details);
        headerReadOnlyTextView = ViewUtils.getView(rootView, R.id.ttv__participants__header);
        headerEditText = ViewUtils.getView(rootView, R.id.taet__participants__header__editable);
        bottomBorder = ViewUtils.getView(rootView, R.id.v_participants__header__bottom_border);
        shieldView = ViewUtils.getView(rootView, R.id.verified_shield);

        penIcon = ViewUtils.getView(rootView, R.id.gtv__participants_header__pen_icon);
        penIcon.setVisibility(View.GONE);

        membersCountTextView.setVisibility(View.GONE);
        userDetailsView.setVisibility(View.GONE);

        headerEditText.setOnTouchListener(headerOnTouchListener);
        headerEditText.setOnEditorActionListener(editorActionListener);

        // Hide bottom border initially
        bottomBorder.setVisibility(View.GONE);


        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getControllerFactory().getConversationScreenController().hideParticipants(true, false);
            }
        });

        return rootView;
    }


    @Override
    public void onStart() {
        super.onStart();
        getStoreFactory().participantsStore().addParticipantsStoreObserver(this);
        getControllerFactory().getConversationScreenController().addConversationControllerObservers(this);
        getControllerFactory().getAccentColorController().addAccentColorObserver(this);

        if (!((BaseActivity) getActivity()).injectJava(ThemeController.class).isDarkTheme()) {
            headerEditText.setAccentColor(getControllerFactory().getAccentColorController().getColor());
        }

        updateWithCurrentConv();
    }

    @Override
    public void onResume() {
        super.onResume();
        headerEditText.clearFocus();
        getControllerFactory().getGlobalLayoutController().addKeyboardVisibilityObserver(this);
        penIcon.setOnClickListener(this);
    }

    @Override
    public void onPause() {
        getControllerFactory().getGlobalLayoutController().removeKeyboardVisibilityObserver(this);
        KeyboardUtils.hideKeyboard(getActivity());
        penIcon.setOnClickListener(null);
        super.onPause();
    }

    @Override
    public void onStop() {
        getControllerFactory().getAccentColorController().removeAccentColorObserver(this);
        getStoreFactory().connectStore().removeConnectRequestObserver(this);
        getControllerFactory().getConversationScreenController().removeConversationControllerObservers(this);
        getStoreFactory().participantsStore().removeParticipantsStoreObserver(this);
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        membersCountTextView = null;
        userDetailsView = null;
        headerReadOnlyTextView = null;
        headerEditText = null;
        bottomBorder = null;
        penIcon = null;
        shieldView = null;
        super.onDestroyView();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.gtv__participants_header__pen_icon:
                triggerEditingOfConversationNameIfInternet();
                break;
        }
    }

    @Override
    public void conversationUpdated(final IConversation conversation) {
        if (conversation == null) {
            return;
        }

        updateWithCurrentConv();
    }

    private void updateWithCurrentConv() {
        convController.withCurrentConv(new Callback<ConversationData>() {
            @Override
            public void callback(final ConversationData conv) {
                participantsController.withCurrentConvGroupOrBot(new Callback<Boolean>() {
                    @Override
                    public void callback(Boolean groupOrBot) {
                        headerReadOnlyTextView.setText(conv.displayName());
                        if (groupOrBot) {
                            membersCountTextView.setVisibility(View.VISIBLE);
                            userDetailsView.setVisibility(View.GONE);
                            if (conv.isActive()) {
                                headerEditText.setText(conv.displayName());
                                headerEditText.setVisibility(View.VISIBLE);
                            } else {
                                headerEditText.setVisibility(View.GONE);
                            }

                            shieldView.setVisibility(View.GONE);
                            penIcon.setVisibility(View.VISIBLE);

                        } else {
                            membersCountTextView.setVisibility(View.GONE);
                            userDetailsView.setVisibility(View.VISIBLE);
                            headerEditText.setVisibility(View.GONE);
                            penIcon.setVisibility(View.GONE);

                            participantsController.withOtherParticipant(new Callback<UserData>() {
                                @Override
                                public void callback(UserData userData) {
                                    shieldView.setVisibility(userData.verified() == Verification.VERIFIED ?
                                        View.VISIBLE : View.GONE);
                                    userDetailsView.setUserId(userData.id());
                                }
                            });
                        }
                    }
                });
            }
        });
    }

    @Override
    public void participantsUpdated(UsersList participants) {
        membersCountTextView.setText(getResources().getQuantityString(R.plurals.participants__sub_header_xx_people,
                                                                      participants.size(), participants.size()));
        membersCountTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                                         getResources().getDimension(R.dimen.wire__text_size__small));
        membersCountTextView.setOnClickListener(null);

        // Hide header bottom border
        bottomBorder.setVisibility(View.GONE);

        new Handler().post(new Runnable() {
            @Override
            public void run() {
                if (getView() != null && getContainer() != null) {
                    final int height = getView().getMeasuredHeight();
                    getControllerFactory().getConversationScreenController().setParticipantHeaderHeight(height);
                }
            }
        });
    }

    @Override
    public void otherUserUpdated(final User otherUser) {
        setParticipant(otherUser);
    }

    private View.OnTouchListener headerOnTouchListener = new View.OnTouchListener() {
        private boolean downAction = false;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_UP && downAction) {
                triggerEditingOfConversationNameIfInternet();
                downAction = false;
            } else if (event.getAction() == MotionEvent.ACTION_DOWN) {
                downAction = true;
            }

            // consume touch event if there is no network.
            return !getStoreFactory().networkStore().hasInternetConnection();

        }
    };

    private void triggerEditingOfConversationNameIfInternet() {
        if (getStoreFactory() == null ||
            getStoreFactory().isTornDown()) {
            return;
        }
        if (MathUtils.floatEqual(headerEditText.getAlpha(), 0f)) {
            // only if not already visible and network is available
            getStoreFactory().networkStore().doIfHasInternetOrNotifyUser(new NetworkAction() {
                @Override
                public void execute(NetworkMode networkMode) {
                    toggleEditModeForConversationName(true);
                }

                @Override
                public void onNoNetwork() {
                    showOfflineRenameError();
                }
            });
        }
    }

    private void showOfflineRenameError() {
        ViewUtils.showAlertDialog(getActivity(),
                                  R.string.alert_dialog__no_network__header,
                                  R.string.rename_conversation__no_network__message,
                                  R.string.alert_dialog__confirmation,
                                  null, true);
    }

    private TextView.OnEditorActionListener editorActionListener = new TextView.OnEditorActionListener() {

        @Override
        public boolean onEditorAction(final TextView textView, int actionId, KeyEvent event) {
            if (actionId == EditorInfo.IME_ACTION_DONE || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                renameConversation();
                closeHeaderEditing();
                return true;
            }
            return false;
        }

        private void closeHeaderEditing() {
            headerEditText.clearFocus();
            final int height = getView().getMeasuredHeight();
            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    getControllerFactory().getConversationScreenController().setParticipantHeaderHeight(height);
                }
            });
            InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(headerEditText.getWindowToken(), 0);
        }

    };

    private void resetName() {
        convController.withCurrentConvName(new Callback<String>() {
            @Override
            public void callback(String convName) {
                headerReadOnlyTextView.setText(convName);
            }
        });
    }

    private void renameConversation() {
        getStoreFactory().networkStore().doIfHasInternetOrNotifyUser(new NetworkAction() {
                                                                      @Override
                                                                      public void execute(NetworkMode networkMode) {
                                                                          updateConversationName();
                                                                      }

                                                                      @Override
                                                                      public void onNoNetwork() {
                                                                          resetName();
                                                                          showOfflineRenameError();
                                                                      }
                                                                  }
                                                                       );
    }

    private void updateConversationName() {
        if (headerEditText == null) {
            return;
        }

        String text = headerEditText.getText().toString().trim();
        convController.setCurrentConvName(text);
        headerReadOnlyTextView.setText(text);
    }


    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Conversation Manager Notifications
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onShowParticipants(View anchorView, boolean isSingleConversation, boolean isMemberOfConversation, boolean showDeviceTabIfSingle) {
        if (!isSingleConversation && isMemberOfConversation) {
            penIcon.setVisibility(View.VISIBLE);
        } else {
            penIcon.setVisibility(View.GONE);
        }
    }

    @Override
    public void onHideParticipants(boolean orButtonPressed, boolean hideByConversationChange, boolean backOrButtonPressed) {}

    @Override
    public void onShowEditConversationName(boolean edit) {
        // do nothing
    }

    @Override
    public void onHeaderViewMeasured(int participantHeaderHeight) {

    }

    @Override
    public void onShowUser(UserId userId) {

    }

    @Override
    public void onHideUser() {

    }

    @Override
    public void onAddPeopleToConversation() {

    }

    @Override
    public void onShowConversationMenu(boolean inConvList, ConvId convId) {

    }

    @Override
    public void onShowCurrentOtrClient() {

    }

    @Override
    public void onHideOtrClient() {

    }

    @Override
    public void onShowLikesList(Message message) {

    }

    @Override
    public void onShowIntegrationDetails(ProviderId providerId, IntegrationId integrationId) {

    }

    @Override
    public void onKeyboardVisibilityChanged(boolean keyboardIsVisible, int keyboardHeight, View currentFocus) {
        if (!keyboardIsVisible) {
            toggleEditModeForConversationName(false);
        }
    }

    public void onConnectUserUpdated(final User user, IConnectStore.UserRequester usertype) {
        if (usertype != IConnectStore.UserRequester.PARTICIPANTS) {
            return;
        }
        setParticipant(user);
    }

    private void setParticipant(User user) {
        headerReadOnlyTextView.setText(user.getName());
        userDetailsView.setUser(user);
        headerEditText.setVisibility(View.GONE);

        new Handler().post(new Runnable() {
            @Override
            public void run() {
                if (getView() != null && getControllerFactory().getConversationScreenController() != null) {
                    final int height = getView().getMeasuredHeight();
                    getControllerFactory().getConversationScreenController().setParticipantHeaderHeight(height);
                }
            }
        });
    }

    @Override
    public void onInviteRequestSent(IConversation conversation) {

    }

    @Override
    public void onAccentColorHasChanged(Object sender, final int color) {
        participantsController.withCurrentConvGroupOrBot(new Callback<Boolean>() {
            @Override
            public void callback(Boolean groupOrBot) {
                if (!groupOrBot && !inject(ThemeController.class).isDarkTheme()) {
                    headerEditText.setAccentColor(color);
                }
            }
        });
    }

    private void toggleEditModeForConversationName(final boolean edit) {
        participantsController.withCurrentConvGroupOrBot(new Callback<Boolean>() {
            @Override
            public void callback(Boolean groupOrBot) {
                if (groupOrBot) {
                    getControllerFactory().getConversationScreenController().editConversationName(edit);
                    if (edit) {
                        headerEditText.setText(headerReadOnlyTextView.getText());
                        headerEditText.setAlpha(1);
                        headerEditText.requestFocus();
                        headerEditText.setSelection(headerEditText.getText().length());
                        KeyboardUtils.showKeyboard(getActivity());
                        headerReadOnlyTextView.setAlpha(0);
                        membersCountTextView.setAlpha(0);
                        penIcon.setVisibility(View.GONE);
                    } else {
                        headerEditText.setAlpha(0);
                        headerEditText.requestLayout();
                        headerReadOnlyTextView.setAlpha(1);
                        headerEditText.clearFocus();
                        membersCountTextView.setAlpha(1);
                        penIcon.setVisibility(View.VISIBLE);
                    }
                }
            }
        });
    }

    public interface Container {
    }
}
