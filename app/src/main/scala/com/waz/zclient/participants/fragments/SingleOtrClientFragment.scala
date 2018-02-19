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

import android.content.{ClipData, ClipboardManager, Context, DialogInterface}
import android.os.Bundle
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.{CompoundButton, TextView}
import com.waz.api.Verification
import com.waz.model.UserId
import com.waz.model.otr.ClientId
import com.waz.sync.SyncResult
import com.waz.threading.Threading
import com.waz.utils.returning
import com.waz.zclient.Intents.ShowDevicesIntent
import com.waz.zclient.common.controllers.BrowserController
import com.waz.zclient.common.controllers.global.ClientsController.getDeviceClassName
import com.waz.zclient.common.controllers.global.{AccentColorController, ClientsController}
import com.waz.zclient.messages.UsersController
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.ui.utils.TextViewUtils.{getBoldHighlightText, getHighlightText}
import com.waz.zclient.ui.views.e2ee.OtrSwitch
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.ViewUtils
import com.waz.zclient.views.LoadingIndicatorView
import com.waz.zclient.{FragmentHelper, R}


class SingleOtrClientFragment extends FragmentHelper with View.OnClickListener {

  import SingleOtrClientFragment._
  implicit val cxt: Context = getActivity

  private lazy val clientsController = inject[ClientsController]
  private lazy val usersController   = inject[UsersController]

  private lazy val userId   = UserId(getArguments.getString(ArgUser))
  private lazy val clientId = ClientId(getArguments.getString(ArgClient))

  private lazy val accentColor = inject[AccentColorController].accentColor.map(_.getColor)
  private lazy val user        = usersController.user(userId)
  private lazy val client      = clientsController.client(userId, clientId).collect { case Some(c) => c }
  private lazy val fingerPrint = clientsController.fingerprint(userId, clientId).collect { case Some(fp) => fp }

  private lazy val typeTextView = returning(view[TextView](R.id.ttv__single_otr_client__type)) { vh =>
    client
      .map(_.devType)
      .map(getDeviceClassName)
      .map(_.toUpperCase())
      .onUi(t => vh.foreach(_.setText(t)))
  }

  private lazy val closeButton = view[TextView](R.id.gtv__single_otr_client__close)
  private lazy val backButton = view[TextView] (R.id.gtv__single_otr_client__back)
  private lazy val idTextView = view[TypefaceTextView](R.id.ttv__single_otr_client__id)

  private lazy val verifySwitch = returning(view[OtrSwitch](R.id.os__single_otr_client__verify)) { vh =>
    client.map(_.verified == Verification.VERIFIED).onUi(ver => vh.foreach(_.setChecked(ver)))
  }

  private lazy val descriptionTextview = returning(view[TextView] (R.id.ttv__single_otr_client__description)) { vh =>
    user
      .map(u => getString(R.string.otr__participant__single_device__description, u.getDisplayName))
      .onUi(t => vh.foreach(_.setText(t)))
  }

  private lazy val howToLinkButton = returning(view[TextView](R.id.ttv__single_otr_client__how_to_link)) { vh =>
    accentColor
      .map(c => getHighlightText(getActivity, getString(R.string.otr__participant__single_device__how_to_link), c, false))
      .onUi(t => vh.foreach(_.setText(t)))
  }

  private lazy val myDevicesButton = returning(view[TextView](R.id.ttv__single_otr_client__my_devices)) { vh =>
    accentColor.onUi(c => vh.foreach(_.setTextColor(c)))
  }

  private lazy val resetSessionButton = returning(view[TextView](R.id.ttv__single_otr_client__reset)) { vh =>
    accentColor.onUi(c => vh.foreach(_.setTextColor(c)))
  }

  private lazy val myFingerprintButton = returning(view[TextView](R.id.ttv__single_otr_client__my_fingerprint)) { vh =>
    accentColor.onUi(c => vh.foreach(_.setTextColor(c)))
  }

  private lazy val fingerprintView = returning(view[TypefaceTextView](R.id.ttv__single_otr_client__fingerprint)) { vh =>
    fingerPrint.map(ClientsController.getFormattedFingerprint).onUi { s =>
      vh.foreach(v => v.setText(getBoldHighlightText(getContext, s, v.getCurrentTextColor, 0, s.length)))
    }
  }

  override def onCreateView(inflater: LayoutInflater, viewGroup: ViewGroup, savedInstanceState: Bundle) =
    inflater.inflate(R.layout.fragment_single_otr_client, viewGroup, false)


  override def onViewCreated(view: View, savedInstanceState: Bundle) = {
    super.onViewCreated(view, savedInstanceState)

    idTextView.foreach(v => v.setText(ClientsController.getFormattedDisplayId(clientId, v.getCurrentTextColor)))
    closeButton
    backButton
    verifySwitch
    descriptionTextview
    howToLinkButton
    myDevicesButton
    resetSessionButton
    myFingerprintButton
    fingerprintView
  }

  private val onCheckChangedListener = new CompoundButton.OnCheckedChangeListener {
    override def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) =
      clientsController.updateVerified(userId, clientId, isChecked)
  }

  override def onClick(v: View) = {
    Option(v).map(_.getId).foreach {
      case R.id.gtv__single_otr_client__back |
           R.id.gtv__single_otr_client__close =>
//        getControllerFactory.getConversationScreenController.hideOtrClient()
      case R.id.ttv__single_otr_client__my_fingerprint =>
//        getControllerFactory.getConversationScreenController.showCurrentOtrClient()
      case R.id.ttv__single_otr_client__reset =>
        resetSession()
      case R.id.ttv__single_otr_client__fingerprint =>
        fingerprintView.foreach { v =>
          val clipboard = getActivity.getSystemService(Context.CLIPBOARD_SERVICE).asInstanceOf[ClipboardManager]
          val clip = ClipData.newPlainText(getString(R.string.pref_devices_device_fingerprint_copy_description), v.getText.toString)
          clipboard.setPrimaryClip(clip)
          showToast(R.string.pref_devices_device_fingerprint_copy_toast)
        }
      case R.id.ttv__single_otr_client__my_devices =>
        startActivity(ShowDevicesIntent(getActivity))
      case R.id.ttv__single_otr_client__how_to_link =>
        inject[BrowserController].openUrl(getString(R.string.url_otr_learn_how))
    }
  }

  private def resetSession(): Unit = {
//    getContainer
//      .getLoadingViewIndicator
//      .show(
//        LoadingIndicatorView.SpinnerWithDimmedBackground$.MODULE$,
//        getActivity.asInstanceOf[BaseActivity].injectJava(classOf[ThemeController]).isDarkTheme)

    resetSessionButton.foreach(_.setEnabled(false))
    clientsController.resetSession(userId, clientId).map { res =>
      resetSessionButton.foreach(_.setEnabled(true))
      res match {
        case SyncResult.Success =>
          ViewUtils.showAlertDialog(
            getActivity,
            R.string.empty_string,
            R.string.otr__reset_session__message_ok,
            R.string.otr__reset_session__button_ok, null, true)
        case SyncResult.Failure(_, _) =>
          ViewUtils.showAlertDialog(
            getActivity,
            R.string.empty_string,
            R.string.otr__reset_session__message_fail,
            R.string.otr__reset_session__button_ok,
            R.string.otr__reset_session__button_fail,
            null,
            new DialogInterface.OnClickListener() {
              override def onClick(dialog: DialogInterface, which: Int) = {
                resetSession()
              }
            })
      }
    } (Threading.Ui)
  }

  override def onResume() = {
    getView.setOnClickListener(this)
    backButton.setOnClickListener(this)
    closeButton.setOnClickListener(this)
    myFingerprintButton.setOnClickListener(this)
    resetSessionButton.setOnClickListener(this)
    verifySwitch.setOnCheckedListener(onCheckChangedListener)
    myDevicesButton.setOnClickListener(this)
    fingerprintView.setOnClickListener(this)
    howToLinkButton.setOnClickListener(this)
    super.onResume()

  }

  override def onPause() = {
    super.onPause()
    getView.setOnClickListener(null)
    backButton.setOnClickListener(null)
    closeButton.setOnClickListener(null)
    myFingerprintButton.setOnClickListener(null)
    resetSessionButton.setOnClickListener(null)
    verifySwitch.setOnCheckedListener(null)
    myDevicesButton.setOnClickListener(null)
    fingerprintView.setOnClickListener(null)
    howToLinkButton.setOnClickListener(null)
  }

}

object SingleOtrClientFragment {
  val TAG = classOf[SingleOtrClientFragment].getName
  private val ArgUser = "ARG_USER"
  private val ArgClient = "ARG_CLIENT"

  def newInstance(userId: UserId, clientId: ClientId) = {
    returning(new SingleOtrClientFragment) {
      _.setArguments(returning(new Bundle()) { b =>
        b.putString(ArgUser, userId.str)
        b.putString(ArgClient, clientId.str)
      })
    }
  }

  trait Container {
    def getLoadingViewIndicator: LoadingIndicatorView

    def onOpenUrl(url: String): Unit
  }

}
