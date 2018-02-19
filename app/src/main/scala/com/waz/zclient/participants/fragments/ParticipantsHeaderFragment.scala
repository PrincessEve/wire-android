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
package com.waz.zclient.participants.fragments

import android.content.Context
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.Toolbar
import android.util.TypedValue
import android.view._
import android.view.animation.{AlphaAnimation, Animation}
import android.view.inputmethod.{EditorInfo, InputMethodManager}
import android.widget.TextView
import com.waz.api.{IConversation, NetworkMode, User, Verification}
import com.waz.model.{UserData, UserId}
import com.waz.utils.events.{EventStream, Signal}
import com.waz.utils.returning
import com.waz.zclient.common.controllers.ThemeController
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.common.views.UserDetailsView
import com.waz.zclient.controllers.globallayout.KeyboardVisibilityObserver
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.core.stores.connect.{ConnectStoreObserver, IConnectStore}
import com.waz.zclient.core.stores.network.NetworkAction
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.participants.ParticipantsController
import com.waz.zclient.ui.text.AccentColorEditText
import com.waz.zclient.ui.utils.{KeyboardUtils, MathUtils}
import com.waz.zclient.utils.{RichView, ViewUtils}
import com.waz.zclient.views.e2ee.ShieldView
import com.waz.zclient.{FragmentHelper, R}
import com.waz.ZLog.ImplicitTag._

class ParticipantsHeaderFragment extends BaseFragment[ParticipantsHeaderFragment.Container]
  with FragmentHelper
  with KeyboardVisibilityObserver
  with ConnectStoreObserver {

  import com.waz.threading.Threading.Implicits.Ui

  private lazy val toolbar                = view[Toolbar](R.id.t__participants__toolbar)
  private lazy val membersCountTextView   = view[TextView](R.id.ttv__participants__sub_header)
  private lazy val userDetailsView        = view[UserDetailsView](R.id.udv__participants__user_details)
  private lazy val headerEditText         = view[AccentColorEditText](R.id.taet__participants__header__editable)
  private lazy val headerReadOnlyTextView = view[TextView](R.id.ttv__participants__header)
  private lazy val bottomBorder           = view[View](R.id.v_participants__header__bottom_border)
  private lazy val penIcon                = view[TextView](R.id.gtv__participants_header__pen_icon)
  private lazy val shieldView             = view[ShieldView](R.id.sv__otr__verified_shield)

  private lazy val convController = inject[ConversationController]
  private lazy val participantsController = inject[ParticipantsController]
  private lazy val themeController = inject[ThemeController]
  private lazy val accentColorController = inject[AccentColorController]

  val onNavigationClicked = EventStream[Unit]()
  val convNameEditMode = Signal(false)

  private val groupConvEditMode = for {
    groupOrBot <- participantsController.isGroupOrBot
    edit <- convNameEditMode
  } yield (groupOrBot, edit)


  private val headerOnTouchListener: View.OnTouchListener = new View.OnTouchListener() {
    private var downAction: Boolean = false

    override def onTouch(v: View, event: MotionEvent): Boolean = {
      if (event.getAction == MotionEvent.ACTION_UP && downAction) {
        triggerEditingOfConversationNameIfInternet()
        downAction = false
      } else if (event.getAction == MotionEvent.ACTION_DOWN) {
        downAction = true
      }
      // consume touch event if there is no network.
      !getStoreFactory.networkStore.hasInternetConnection
    }
  }

  private val editorActionListener: TextView.OnEditorActionListener = new TextView.OnEditorActionListener() {
    override def onEditorAction(textView: TextView, actionId: Int, event: KeyEvent): Boolean =
      if (actionId == EditorInfo.IME_ACTION_DONE || (event != null && event.getKeyCode == KeyEvent.KEYCODE_ENTER)) {
        renameConversation()
        closeHeaderEditing()
        true
      } else false

    private def renameConversation() = getStoreFactory.networkStore.doIfHasInternetOrNotifyUser(new NetworkAction() {
      override def execute(networkMode: NetworkMode): Unit = headerEditText.foreach { he =>
        val text = he.getText.toString.trim
        convController.setCurrentConvName(text)
        headerReadOnlyTextView.foreach(_.setText(text))
      }

      override def onNoNetwork(): Unit = {
        convController.currentConv.map(_.displayName).head.foreach { name =>
          headerReadOnlyTextView.foreach(_.setText(name))
        }
        showOfflineRenameError()
      }
    })

    private def closeHeaderEditing(): Unit = headerEditText.foreach { he =>
      he.clearFocus()
      getActivity
        .getSystemService(Context.INPUT_METHOD_SERVICE).asInstanceOf[InputMethodManager]
        .hideSoftInputFromWindow(he.getWindowToken, 0)
    }
  }

  private def triggerEditingOfConversationNameIfInternet(): Unit =
    if (Option(getStoreFactory).isDefined &&
      !getStoreFactory.isTornDown &&
      MathUtils.floatEqual(headerEditText.getAlpha, 0f)) { // only if not already visible and network is available
      getStoreFactory.networkStore.doIfHasInternetOrNotifyUser(new NetworkAction() {
        override def execute(networkMode: NetworkMode): Unit = convNameEditMode ! true
        override def onNoNetwork(): Unit = showOfflineRenameError()
      })
    }

  private def showOfflineRenameError(): Unit =
    ViewUtils.showAlertDialog(
      getActivity,
      R.string.alert_dialog__no_network__header,
      R.string.rename_conversation__no_network__message,
      R.string.alert_dialog__confirmation,
      null,
      true
    )

  private def setParticipant(userId: UserId): Unit = {
    participantsController.getUser(userId).foreach {
      case Some(user) => headerReadOnlyTextView.foreach(_.setText(user.displayName))
      case None =>
    }
    userDetailsView.foreach(_.setUserId(userId))
    headerEditText.foreach(_.setVisible(false))
  }


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

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    inflater.inflate(R.layout.fragment_participants_header, container, false)
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)

    toolbar.foreach(_.setNavigationOnClickListener(new View.OnClickListener() {
      override def onClick(v: View): Unit = onNavigationClicked ! Unit
    }))

    membersCountTextView
    userDetailsView

    headerEditText.foreach { he =>
      he.setOnTouchListener(headerOnTouchListener)
      he.setOnEditorActionListener(editorActionListener)
    }

    headerReadOnlyTextView
    bottomBorder
    penIcon
    shieldView

    onNavigationClicked { _ =>
      getControllerFactory.getConversationScreenController.hideParticipants(true, false)
    }

    (for {
      darkTheme <- themeController.darkThemeSet
      accentColor <- accentColorController.accentColor
    } yield (darkTheme, accentColor)) {
      case (true, color) => headerEditText.foreach(_.setAccentColor(color.getColor))
      case _ =>
    }

    groupConvEditMode.map { case (groupOrBot, edit) => groupOrBot && !edit } { penIconVisible =>
      penIcon.foreach(_.setVisible(penIconVisible))
    }

    groupConvEditMode.map { case (groupOrBot, edit) => groupOrBot && edit } {
      case true => KeyboardUtils.showKeyboard(getActivity)
      case false =>
    }

    groupConvEditMode {
      case (false, _) =>
      case (true, true) =>
        headerReadOnlyTextView.foreach { view =>
          headerEditText.foreach { he =>
            he.setText(view.getText)
            he.setAlpha(1)
            he.requestFocus()
            he.setSelection(view.getText.length)
          }
        }
        headerReadOnlyTextView.foreach(_.setAlpha(0))
        membersCountTextView.foreach(_.setAlpha(0))
      case (true, false) =>
        headerEditText.foreach { he =>
          he.setAlpha(0)
          he.clearFocus()
          he.requestLayout()
        }
        headerReadOnlyTextView.foreach(_.setAlpha(1))
        membersCountTextView.foreach(_.setAlpha(1))
    }

    groupConvEditMode {
      case (true, edit) => getControllerFactory.getConversationScreenController.editConversationName(edit)
      case _ =>
    }

    (for {
      conv         <- convController.currentConv
      isGroupOrBot <- participantsController.isGroupOrBot
    } yield (conv.isActive && isGroupOrBot, conv.displayName)) {
      case (false, _)          => headerEditText.foreach(_.setVisible(false))
      case (true, displayName) =>
        headerEditText.foreach { he =>
          he.setText(displayName)
          he.setVisible(true)
        }
    }

    (for {
      isGroupOrBot <- participantsController.isGroupOrBot
      user         <- if (isGroupOrBot) Signal.const(Option.empty[UserData])
      else participantsController.otherParticipant.flatMap {
        case Some(userId) => Signal.future(participantsController.getUser(userId))
        case None         => Signal.const(Option.empty[UserData])
      }
    } yield user.fold(false)(_.verified == Verification.VERIFIED)) {
      isVerified => shieldView.foreach(_.setVisible(isVerified))
    }

    (for {
      isGroupOrBot <- participantsController.isGroupOrBot
      userId       <- if (isGroupOrBot) Signal.const(Option.empty[UserId]) else participantsController.otherParticipant
    } yield userId) {
      case Some(userId) => userDetailsView.foreach(_.setUserId(userId))
      case _            =>
    }

    participantsController.otherParticipants.map(_.size) { participants =>
      membersCountTextView.foreach { mc =>
        mc.setText(getResources.getQuantityString(R.plurals.participants__sub_header_xx_people, participants))
        mc.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources.getDimension(R.dimen.wire__text_size__small))
      }
      bottomBorder.foreach(_.setVisible(false))
    }

    participantsController.otherParticipant {
      case Some(userId) => setParticipant(userId)
      case _ =>
    }

    convController.currentConv { conv =>
      headerReadOnlyTextView.foreach(_.setText(conv.displayName))
    }

    participantsController.isGroupOrBot { isGroupOrBot =>
      membersCountTextView.foreach(_.setVisible(isGroupOrBot))
      userDetailsView.foreach(_.setVisible(!isGroupOrBot))
    }

  }

  override def onResume(): Unit = {
    super.onResume()
    headerEditText.foreach(_.clearFocus())
    getControllerFactory.getGlobalLayoutController.addKeyboardVisibilityObserver(this)
    penIcon.foreach(_.onClick { triggerEditingOfConversationNameIfInternet() })
  }

  override def onPause(): Unit = {
    getControllerFactory.getGlobalLayoutController.removeKeyboardVisibilityObserver(this)
    KeyboardUtils.hideKeyboard(getActivity)
    penIcon.foreach(_.setOnClickListener(null))
    super.onPause()
  }

  override def onStop(): Unit = {
    getStoreFactory.connectStore.removeConnectRequestObserver(this)
    super.onStop()
  }

  override def onDestroyView(): Unit = {

    super.onDestroyView()
  }

  override def onKeyboardVisibilityChanged(keyboardIsVisible: Boolean, keyboardHeight: Int, currentFocus: View): Unit =
    if (!keyboardIsVisible) convNameEditMode ! false

  override def onConnectUserUpdated(user: User, userType: IConnectStore.UserRequester): Unit =
    if (userType == IConnectStore.UserRequester.PARTICIPANTS) setParticipant(UserId(user.getId))

  override def onInviteRequestSent(conversation: IConversation): Unit = {}

}

object ParticipantsHeaderFragment {
  val TAG: String = classOf[ParticipantsHeaderFragment].getName

  def newInstance(): ParticipantsHeaderFragment = new ParticipantsHeaderFragment

  trait Container {

  }

}
