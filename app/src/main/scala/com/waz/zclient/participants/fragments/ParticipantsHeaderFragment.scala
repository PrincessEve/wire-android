package com.waz.zclient.participants.fragments

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.support.v4.app.Fragment
import android.support.v7.widget.Toolbar
import android.util.TypedValue
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import com.waz.api.IConversation
import com.waz.api.Message
import com.waz.api.NetworkMode
import com.waz.api.OtrClient
import com.waz.api.User
import com.waz.api.UsersList
import com.waz.api.Verification
import com.waz.model.ConvId
import com.waz.model.ConversationData
import com.waz.model.IntegrationId
import com.waz.model.ProviderId
import com.waz.model.UserData
import com.waz.model.UserId
import com.waz.utils.returning
import com.waz.zclient.{BaseActivity, FragmentHelper, R}
import com.waz.zclient.common.controllers.ThemeController
import com.waz.zclient.common.views.UserDetailsView
import com.waz.zclient.controllers.accentcolor.AccentColorObserver
import com.waz.zclient.controllers.currentfocus.IFocusController
import com.waz.zclient.controllers.globallayout.KeyboardVisibilityObserver
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.core.stores.connect.ConnectStoreObserver
import com.waz.zclient.core.stores.connect.IConnectStore
import com.waz.zclient.core.stores.network.NetworkAction
import com.waz.zclient.core.stores.participants.ParticipantsStoreObserver
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.pages.main.conversation.controller.ConversationScreenControllerObserver
import com.waz.zclient.participants.ParticipantsController
import com.waz.zclient.ui.text.AccentColorEditText
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.ui.utils.MathUtils
import com.waz.zclient.utils.Callback
import com.waz.zclient.utils.LayoutSpec
import com.waz.zclient.utils.ViewUtils
import com.waz.zclient.views.e2ee.ShieldView
import com.waz.zclient.utils.ContextUtils._

class ParticipantsHeaderFragment extends BaseFragment[ParticipantsHeaderFragment.Container]
  with FragmentHelper
  with KeyboardVisibilityObserver
  with ParticipantsStoreObserver
  with View.OnClickListener
  with ConversationScreenControllerObserver
  with ConnectStoreObserver
  with AccentColorObserver {

  private lazy val toolbar = view[Toolbar](R.id.t__participants__toolbar)

  private var membersCountTextView: TextView = null
  private var userDetailsView: UserDetailsView = null
  private var headerEditText: AccentColorEditText = null
  private var headerReadOnlyTextView: TextView = null
  private var bottomBorder: View = null
  private var penIcon: TextView = null
  private var shieldView: ShieldView = null
  private var userRequester: IConnectStore.UserRequester = null

  private val convController = inject[ConversationController]
  private val participantsController = inject[ParticipantsController]

  // This is a workaround for the bug where child fragments disappear when
  // the parent is removed (as all children are first removed from the parent)
  // See https://code.google.com/p/android/issues/detail?id=55228
  // Apply the workaround only if this is a child fragment, and the parent is being removed.
  override def onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation = Option(getParentFragment) match {
    case Some(parent: Fragment) if enter && parent.isRemoving => returning(new AlphaAnimation(1, 1)){
      _.setDuration(ViewUtils.getNextAnimationDuration(parent))
    }
    case _ => super.onCreateAnimation(transit, enter, nextAnim)
  }

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    userRequester = getArguments.getSerializable(ParticipantsHeaderFragment.ARG_USER_REQUESTER).asInstanceOf[IConnectStore.UserRequester]
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    val rootView: View = inflater.inflate(R.layout.fragment_participants_header, container, false)
    toolbar = ViewUtils.getView(rootView, R.id.t__participants__toolbar)
    membersCountTextView = ViewUtils.getView(rootView, R.id.ttv__participants__sub_header)
    userDetailsView = ViewUtils.getView(rootView, R.id.udv__participants__user_details)
    headerReadOnlyTextView = ViewUtils.getView(rootView, R.id.ttv__participants__header)
    headerEditText = ViewUtils.getView(rootView, R.id.taet__participants__header__editable)
    bottomBorder = ViewUtils.getView(rootView, R.id.v_participants__header__bottom_border)
    shieldView = ViewUtils.getView(rootView, R.id.sv__otr__verified_shield)
    penIcon = ViewUtils.getView(rootView, R.id.gtv__participants_header__pen_icon)
    headerEditText.setOnTouchListener(headerOnTouchListener)
    headerEditText.setOnEditorActionListener(editorActionListener)

      toolbar.setNavigationOnClickListener(new View.OnClickListener() {
        override def onClick(v: View): Unit = {
          if (userRequester == IConnectStore.UserRequester.POPOVER) {
            getContainer.dismissDialog()
          }
          else {
            getControllerFactory.getConversationScreenController.hideParticipants(true, false)
          }
        }
      })

    rootView
  }

  override def onStart(): Unit = {
    super.onStart()
    if (userRequester eq IConnectStore.UserRequester.POPOVER) {
      getStoreFactory.connectStore.addConnectRequestObserver(this)
      val user: User = getStoreFactory.singleParticipantStore.getUser
      getStoreFactory.connectStore.loadUser(user.getId, userRequester)
    }
    else {
      getStoreFactory.participantsStore.addParticipantsStoreObserver(this)
    }
    getControllerFactory.getConversationScreenController.addConversationControllerObservers(this)
    getControllerFactory.getAccentColorController.addAccentColorObserver(this)
    if (!((getActivity.asInstanceOf[BaseActivity]).injectJava(classOf[ThemeController]).isDarkTheme)) {
      headerEditText.setAccentColor(getControllerFactory.getAccentColorController.getColor)
    }
    updateWithCurrentConv()
  }

  override def onResume(): Unit = {
    super.onResume()
    headerEditText.clearFocus()
    getControllerFactory.getGlobalLayoutController.addKeyboardVisibilityObserver(this)
    penIcon.setOnClickListener(this)
  }

  override def onPause(): Unit = {
    getControllerFactory.getGlobalLayoutController.removeKeyboardVisibilityObserver(this)
    KeyboardUtils.hideKeyboard(getActivity)
    penIcon.setOnClickListener(null)
    super.onPause()
  }

  override def onStop(): Unit = {
    getControllerFactory.getAccentColorController.removeAccentColorObserver(this)
    getStoreFactory.connectStore.removeConnectRequestObserver(this)
    getControllerFactory.getConversationScreenController.removeConversationControllerObservers(this)
    getStoreFactory.participantsStore.removeParticipantsStoreObserver(this)
    super.onStop()
  }

  override def onDestroyView(): Unit = {
    membersCountTextView = null
    userDetailsView = null
    headerReadOnlyTextView = null
    headerEditText = null
    bottomBorder = null
    penIcon = null
    shieldView = null
    super.onDestroyView()
  }

  override def onClick(v: View): Unit = {
    v.getId match {
      case R.id.gtv__participants_header__pen_icon =>
        triggerEditingOfConversationNameIfInternet()
        break //todo: break is not supported
    }
  }

  override def conversationUpdated(conversation: IConversation): Unit = {
    if (conversation == null) {
      return
    }
    updateWithCurrentConv()
  }

  private def updateWithCurrentConv(): Unit = {
    convController.withCurrentConv(new Callback[ConversationData]() {
      override def callback(conv: ConversationData): Unit = {
        participantsController.withCurrentConvGroupOrBot(new Callback[Boolean]() {
          override def callback(groupOrBot: Boolean): Unit = {
            headerReadOnlyTextView.setText(conv.displayName)
            if (groupOrBot) {
              membersCountTextView.setVisibility(View.VISIBLE)
              userDetailsView.setVisibility(View.GONE)
              if (conv.isActive) {
                headerEditText.setText(conv.displayName)
                headerEditText.setVisibility(View.VISIBLE)
              }
              else {
                headerEditText.setVisibility(View.GONE)
              }
              shieldView.setVisibility(View.GONE)
              penIcon.setVisibility(View.VISIBLE)
            }
            else {
              membersCountTextView.setVisibility(View.GONE)
              userDetailsView.setVisibility(View.VISIBLE)
              headerEditText.setVisibility(View.GONE)
              penIcon.setVisibility(View.GONE)
              participantsController.withOtherParticipant(new Callback[UserData]() {
                override def callback(userData: UserData): Unit = {
                  shieldView.setVisibility(if ((userData.verified eq Verification.VERIFIED)) {
                    View.VISIBLE
                  }
                  else {
                    View.GONE
                  })
                  userDetailsView.setUserId(userData.id)
                }
              })
            }
          }
        })
      }
    })
  }

  override def participantsUpdated(participants: UsersList): Unit = {
    membersCountTextView.setText(getResources.getQuantityString(R.plurals.participants__sub_header_xx_people, participants.size, participants.size))
    membersCountTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources.getDimension(R.dimen.wire__text_size__small))
    membersCountTextView.setOnClickListener(null)
    // Hide header bottom border
    bottomBorder.setVisibility(View.GONE)
    new Handler().post(new Runnable() {
      override def run(): Unit = {
        if (getView != null && getContainer != null) {
          val height: Int = getView.getMeasuredHeight
          getControllerFactory.getConversationScreenController.setParticipantHeaderHeight(height)
        }
      }
    })
  }

  override def otherUserUpdated(otherUser: User): Unit = {
    setParticipant(otherUser)
  }

  private val headerOnTouchListener: View.OnTouchListener = new View.OnTouchListener() {
    private var downAction: Boolean = false
    override

    def onTouch(v: View, event: MotionEvent): Boolean = {
      if (event.getAction == MotionEvent.ACTION_UP && downAction) {
        triggerEditingOfConversationNameIfInternet()
        downAction = false
      }
      else {
        if (event.getAction == MotionEvent.ACTION_DOWN) {
          downAction = true
        }
      }
      // consume touch event if there is no network.
      return !(getStoreFactory.networkStore.hasInternetConnection)
    }
  }

  private def triggerEditingOfConversationNameIfInternet(): Unit = {
    if (getStoreFactory == null || getStoreFactory.isTornDown) {
      return
    }
    if (MathUtils.floatEqual(headerEditText.getAlpha, 0f)) { // only if not already visible and network is available
      getStoreFactory.networkStore.doIfHasInternetOrNotifyUser(new NetworkAction() {
        override def execute(networkMode: NetworkMode): Unit = {
          toggleEditModeForConversationName(true)
        }

        override

        def onNoNetwork(): Unit = {
          showOfflineRenameError()
        }
      })
    }
  }

  private def showOfflineRenameError(): Unit = {
    ViewUtils.showAlertDialog(getActivity, R.string.alert_dialog__no_network__header, R.string.rename_conversation__no_network__message, R.string.alert_dialog__confirmation, null, true)
  }

  private val editorActionListener: TextView.OnEditorActionListener = new TextView.OnEditorActionListener() {
    override def onEditorAction(textView: TextView, actionId: Int, event: KeyEvent): Boolean = {
      if (actionId == EditorInfo.IME_ACTION_DONE || (event != null && event.getKeyCode == KeyEvent.KEYCODE_ENTER)) {
        if (LayoutSpec.isPhone(getActivity)) {
          renameConversation()
        }
        closeHeaderEditing()
        return true
      }
      return false
    }

    private

    def closeHeaderEditing(): Unit = {
      headerEditText.clearFocus()
      val height: Int = getView.getMeasuredHeight
      new Handler().post(new Runnable() {
        override def run(): Unit = {
          getControllerFactory.getConversationScreenController.setParticipantHeaderHeight(height)
        }
      })
      val imm: InputMethodManager = getActivity.getSystemService(Context.INPUT_METHOD_SERVICE).asInstanceOf[InputMethodManager]
      imm.hideSoftInputFromWindow(headerEditText.getWindowToken, 0)
    }
  }

  private def resetName(): Unit = {
    convController.withCurrentConvName(new Callback[String]() {
      override def callback(convName: String): Unit = {
        headerReadOnlyTextView.setText(convName)
      }
    })
  }

  private def renameConversation(): Unit = {
    getStoreFactory.networkStore.doIfHasInternetOrNotifyUser(new NetworkAction() {
      override def execute(networkMode: NetworkMode): Unit = {
        updateConversationName()
      }

      override

      def onNoNetwork(): Unit = {
        resetName()
        showOfflineRenameError()
      }
    })
  }

  private def updateConversationName(): Unit = {
    if (headerEditText == null) {
      return
    }
    val text: String = headerEditText.getText.toString.trim
    convController.setCurrentConvName(text)
    headerReadOnlyTextView.setText(text)
  }

  override def onShowParticipants(anchorView: View, isSingleConversation: Boolean, isMemberOfConversation: Boolean, showDeviceTabIfSingle: Boolean): Unit = {
    if (!(isSingleConversation) && isMemberOfConversation) {
      penIcon.setVisibility(View.VISIBLE)
    }
    else {
      penIcon.setVisibility(View.GONE)
    }
  }

  override def onHideParticipants(orButtonPressed: Boolean, hideByConversationChange: Boolean, backOrButtonPressed: Boolean): Unit = {
    if (LayoutSpec.isTablet(getActivity) && !(hideByConversationChange)) {
      renameConversation()
    }
  }

  override def onShowEditConversationName(edit: Boolean): Unit = {
    // do nothing
  }

  override def onHeaderViewMeasured(participantHeaderHeight: Int): Unit = {
  }

  override def onScrollParticipantsList(verticalOffset: Int, scrolledToBottom: Boolean): Unit = {
    if (bottomBorder == null) {
      return
    }
    if (verticalOffset > 0) {
      bottomBorder.setVisibility(View.VISIBLE)
    }
    else {
      bottomBorder.setVisibility(View.GONE)
    }
  }

  override def onShowUser(userId: UserId): Unit = {
  }

  override def onHideUser(): Unit = {
  }

  override def onAddPeopleToConversation(): Unit = {
  }

  override def onShowConversationMenu(inConvList: Boolean, convId: ConvId): Unit = {
  }

  override def onShowOtrClient(otrClient: OtrClient, user: User): Unit = {
  }

  override def onShowCurrentOtrClient(): Unit = {
  }

  override def onHideOtrClient(): Unit = {
  }

  override def onShowLikesList(message: Message): Unit = {
  }

  override def onShowIntegrationDetails(providerId: ProviderId, integrationId: IntegrationId): Unit = {
  }

  override def onKeyboardVisibilityChanged(keyboardIsVisible: Boolean, keyboardHeight: Int, currentFocus: View): Unit = {
    if (!(keyboardIsVisible)) {
      toggleEditModeForConversationName(false)
    }
  }

  override def onConnectUserUpdated(user: User, usertype: IConnectStore.UserRequester): Unit = {
    if (usertype ne userRequester) {
      return
    }
    setParticipant(user)
  }

  private def setParticipant(user: User): Unit = {
    headerReadOnlyTextView.setText(user.getName)
    userDetailsView.setUser(user)
    headerEditText.setVisibility(View.GONE)
    new Handler().post(new Runnable() {
      override def run(): Unit = {
        if (getView != null && getControllerFactory.getConversationScreenController != null) {
          val height: Int = getView.getMeasuredHeight
          getControllerFactory.getConversationScreenController.setParticipantHeaderHeight(height)
        }
      }
    })
  }

  override def onInviteRequestSent(conversation: IConversation): Unit = {
  }

  override def onAccentColorHasChanged(sender: Any, color: Int): Unit = {
    participantsController.withCurrentConvGroupOrBot(new Callback[Boolean]() {
      override def callback(groupOrBot: Boolean): Unit = {
        if (!(groupOrBot) && !(inject(classOf[ThemeController]).isDarkTheme)) {
          headerEditText.setAccentColor(color)
        }
      }
    })
  }

  private def toggleEditModeForConversationName(edit: Boolean): Unit = {
    participantsController.withCurrentConvGroupOrBot(new Callback[Boolean]() {
      override def callback(groupOrBot: Boolean): Unit = {
        if (groupOrBot) {
          getControllerFactory.getConversationScreenController.editConversationName(edit)
          if (edit) {
            headerEditText.setText(headerReadOnlyTextView.getText)
            headerEditText.setAlpha(1)
            headerEditText.requestFocus
            getControllerFactory.getFocusController.setFocus(IFocusController.CONVERSATION_EDIT_NAME)
            headerEditText.setSelection(headerEditText.getText.length)
            KeyboardUtils.showKeyboard(getActivity)
            headerReadOnlyTextView.setAlpha(0)
            membersCountTextView.setAlpha(0)
            penIcon.setVisibility(View.GONE)
          }
          else {
            headerEditText.setAlpha(0)
            headerEditText.requestLayout()
            headerReadOnlyTextView.setAlpha(1)
            headerEditText.clearFocus()
            membersCountTextView.setAlpha(1)
            penIcon.setVisibility(View.VISIBLE)
            if (LayoutSpec.isTablet(getActivity)) {
              renameConversation()
            }
          }
        }
      }
    })
  }
}

object ParticipantsHeaderFragment {
  val TAG: String = classOf[ParticipantsHeaderFragment].getName
  private val ARG_USER_REQUESTER: String = "ARG_USER_REQUESTER"

  def newInstance(userRequester: IConnectStore.UserRequester): ParticipantsHeaderFragment =
    returning(new ParticipantsHeaderFragment) {
      _.setArguments(returning(new Bundle) {
        _.putSerializable(ARG_USER_REQUESTER, userRequester)
      })
    }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  //  Conversation Manager Notifications
  //
  //////////////////////////////////////////////////////////////////////////////////////////
  trait Container {
    def dismissDialog(): Unit
  }

}
