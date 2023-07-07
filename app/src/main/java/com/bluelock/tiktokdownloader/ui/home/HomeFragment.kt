package com.bluelock.tiktokdownloader.ui.home

import android.Manifest
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bluelock.tiktokdownloader.R
import com.bluelock.tiktokdownloader.databinding.FragmentHomeBinding
import com.bluelock.tiktokdownloader.model.VideoModel
import com.bluelock.tiktokdownloader.remote.RemoteConfig
import com.bluelock.tiktokdownloader.ui.base.BaseFragment
import com.bluelock.tiktokdownloader.util.State
import com.bluelock.tiktokdownloader.util.Utils
import com.bluelock.tiktokdownloader.util.rootFile
import com.bluelock.tiktokdownloader.util.saveVideo
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.ads.GoogleManager
import com.example.ads.databinding.MediumNativeAdLayoutBinding
import com.example.ads.databinding.NativeAdBannerLayoutBinding
import com.example.ads.newStrategy.types.GoogleInterstitialType
import com.example.ads.ui.binding.loadNativeAd
import com.example.analytics.dependencies.Analytics
import com.example.analytics.events.AnalyticsEvent
import com.example.analytics.qualifiers.GoogleAnalytics
import com.example.tiktokdownloaderdemo.viewmodels.MyViewModel
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.system.exitProcess

@AndroidEntryPoint
class HomeFragment : BaseFragment<FragmentHomeBinding>() {
    override val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> FragmentHomeBinding =
        FragmentHomeBinding::inflate

    private lateinit var myViewModel: MyViewModel
    private val permission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        }
    private var nativeAd: NativeAd? = null

    @Inject
    lateinit var googleManager: GoogleManager

    @Inject
    @GoogleAnalytics
    lateinit var analytics: Analytics

    @Inject
    lateinit var remoteConfig: RemoteConfig

    lateinit var downloadingDialog: BottomSheetDialog

    override fun onCreatedView() {
        permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        myViewModel = ViewModelProvider(this)[MyViewModel::class.java]

        requireActivity().onBackPressedDispatcher.addCallback(this) {
            requireActivity().finish();
            exitProcess(0);
        }

        showNativeAd()
        observe()
        initUI()
        lifecycleScope.launch {
            delay(3000)
            showDropDown()
        }
    }

    override fun onDestroyedView() {
        Log.d("jeje", "Home Fragment")
    }

    private fun observe() {
        binding.apply {
            myViewModel.responseLiveData.observe(requireActivity()) {
                if (checkIfFileExist(it)) {
                    Toast.makeText(requireActivity(), "File Already Exist", Toast.LENGTH_SHORT)
                        .show()
                    return@observe
                }
                lifecycleScope.launch {

                    downloadingDialog = BottomSheetDialog(requireActivity(), R.style.SheetDialog)
                    downloadingDialog.setContentView(R.layout.dialog_downloading)
                    val adView =
                        downloadingDialog.findViewById<FrameLayout>(R.id.nativeViewAdDownload)
                    if (remoteConfig.nativeAd) {
                        nativeAd = googleManager.createNativeAdSmall()
                        nativeAd?.let {
                            val nativeAdLayoutBinding =
                                NativeAdBannerLayoutBinding.inflate(layoutInflater)
                            nativeAdLayoutBinding.nativeAdView.loadNativeAd(ad = it)
                            adView?.removeAllViews()
                            adView?.addView(nativeAdLayoutBinding.root)
                            adView?.visibility = View.VISIBLE
                        }
                    }

                    downloadingDialog.behavior.isDraggable = false
                    downloadingDialog.setCanceledOnTouchOutside(false)
                    downloadingDialog.show()

                    Glide.with(requireActivity()).asFile().load(it.videoUrl)
                        .into(object : CustomTarget<File>() {
                            override fun onResourceReady(
                                resource: File,
                                transition: Transition<in File>?
                            ) {

                                lifecycleScope.launch(Dispatchers.IO) {
                                    val finished =
                                        async { saveVideo(resource, it, requireActivity()) }
                                    val state = finished.await()
                                    if (state is State.COMPLETE) {


                                        withContext(Dispatchers.Main) {
                                            downloadingDialog.dismiss()
                                            delay(1000)
                                            val dialog = BottomSheetDialog(
                                                requireActivity(),
                                                R.style.SheetDialog
                                            )
                                            dialog.setContentView(R.layout.dialog_download_success)
                                            val btnOk = dialog.findViewById<Button>(R.id.btn_clear)
                                            val btnClose =
                                                dialog.findViewById<ImageView>(R.id.ivCrossSuc)
                                            val adView =
                                                dialog.findViewById<FrameLayout>(R.id.nativeViewAdSuccess)
                                            dialog.behavior.isDraggable = false
                                            dialog.setCanceledOnTouchOutside(false)
                                            if (showNatAd()) {
                                                nativeAd = googleManager.createNativeAdSmall()
                                                nativeAd?.let {
                                                    val nativeAdLayoutBinding =
                                                        NativeAdBannerLayoutBinding.inflate(
                                                            layoutInflater
                                                        )
                                                    nativeAdLayoutBinding.nativeAdView.loadNativeAd(
                                                        ad = it
                                                    )
                                                    adView?.removeAllViews()
                                                    adView?.addView(nativeAdLayoutBinding.root)
                                                    adView?.visibility = View.VISIBLE
                                                }
                                            }

                                            btnOk?.setOnClickListener {
                                                dialog.dismiss()
                                                showInterstitialAd {
                                                    findNavController().navigate(
                                                        HomeFragmentDirections.actionHomeFragmentToDownloadedFragment()
                                                    )

                                                }
                                            }
                                            btnClose?.setOnClickListener {
                                                dialog.dismiss()
                                                showInterstitialAd {
                                                }
                                            }

                                            dialog.show()
                                        }


                                    } else {
                                        val dialog = BottomSheetDialog(requireActivity())
                                        dialog.setContentView(R.layout.dialog_bottom_video_not_found)
                                        val btnOk = dialog.findViewById<Button>(R.id.btn_clear)

                                        val adView =
                                            dialog.findViewById<FrameLayout>(R.id.nativeViewNot)
                                        if (remoteConfig.nativeAd) {
                                            nativeAd = googleManager.createNativeAdSmall()
                                            nativeAd?.let {
                                                val nativeAdLayoutBinding =
                                                    NativeAdBannerLayoutBinding.inflate(
                                                        layoutInflater
                                                    )
                                                nativeAdLayoutBinding.nativeAdView.loadNativeAd(ad = it)
                                                adView?.removeAllViews()
                                                adView?.addView(nativeAdLayoutBinding.root)
                                                adView?.visibility = View.VISIBLE
                                            }
                                        }

                                        dialog.behavior.isDraggable = false
                                        dialog.setCanceledOnTouchOutside(false)

                                        btnOk?.setOnClickListener {
                                            showInterstitialAd {
                                                dialog.dismiss()
                                            }
                                        }
                                        dialog.show()
                                    }

                                }
                            }

                            override fun onLoadCleared(placeholder: Drawable?) {

                            }
                        })
                }
            }

        }
    }

    private fun initUI() {
        binding.apply {
            ivCross.setOnClickListener {
                showInterstitialAd {
                    etLink.text = null
                }
            }

            btnDownloaded.setOnClickListener {
                showInterstitialAd {
                    findNavController().navigate(HomeFragmentDirections.actionHomeFragmentToDownloadedFragment())
                }
            }

            btnSetting.setOnClickListener {
                showInterstitialAd {
                    findNavController().navigate(HomeFragmentDirections.actionHomeFragmentToSettingFragment())
                }
            }
            etLink.addTextChangedListener(object : TextWatcher {
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    if (s.toString().trim { it <= ' ' }.isEmpty()) {
                        btnDownload.isEnabled = false
                        ivCross.visibility = View.GONE

                    } else {
                        btnDownload.isEnabled = true
                        ivCross.visibility = View.VISIBLE
                    }
                }

                override fun beforeTextChanged(
                    s: CharSequence, start: Int, count: Int,
                    after: Int
                ) {
                    Log.d("jejeText", "before")
                }

                override fun afterTextChanged(s: Editable) {
                    Log.d("jejeYes", "after")
                }
            })


            btnDownload.setOnClickListener {
                val ll = etLink.text.toString().trim { it <= ' ' }
                analytics.logEvent(
                    AnalyticsEvent.LINK(
                        status = ll
                    )
                )
                if (ll == "") {
                    Utils.setToast(requireActivity(), resources.getString(R.string.enter_url))
                } else if (!Patterns.WEB_URL.matcher(ll).matches()) {
                    Utils.setToast(
                        requireActivity(),
                        resources.getString(R.string.enter_valid_url)
                    )
                } else {
                    if (ll.isNotEmpty() && ll.contains("tiktok")) {
                        lifecycleScope.launch {
                            val dialog = BottomSheetDialog(requireContext(), R.style.SheetDialog)
                            dialog.setContentView(R.layout.layout_progress_dialog)
                            val adView = dialog.findViewById<FrameLayout>(R.id.nativeViewAdDownload)
                            if (remoteConfig.nativeAd) {
                                nativeAd = googleManager.createNativeAdSmall()
                                nativeAd?.let {
                                    val nativeAdLayoutBinding =
                                        NativeAdBannerLayoutBinding.inflate(layoutInflater)
                                    nativeAdLayoutBinding.nativeAdView.loadNativeAd(ad = it)
                                    adView?.removeAllViews()
                                    adView?.addView(nativeAdLayoutBinding.root)
                                    adView?.visibility = View.VISIBLE
                                }
                            }

                            dialog.behavior.isDraggable = false
                            dialog.setCanceledOnTouchOutside(false)
                            dialog.show()
                            delay(3000)
                            dialog.dismiss()
                            delay(100)
                            downloadVideo(ll)
                        }

                    } else Toast.makeText(
                        requireActivity(),
                        "Enter Valid Url!!",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                analytics.logEvent(
                    AnalyticsEvent.BTNDownload(
                        status = "Clicked"
                    )
                )
            }
        }
    }

    private fun downloadVideo(link: String) {
        lifecycleScope.launch {
            val dialog = BottomSheetDialog(requireActivity(), R.style.SheetDialog)
            dialog.setContentView(R.layout.dialog_bottom_start_download)
            val videoQualityTv = dialog.findViewById<Button>(R.id.btn_clear)
            val adView = dialog.findViewById<FrameLayout>(R.id.nativeViewAdDownload)
            if (remoteConfig.nativeAd) {
                nativeAd = googleManager.createNativeAdSmall()
                nativeAd?.let {
                    val nativeAdLayoutBinding = NativeAdBannerLayoutBinding.inflate(layoutInflater)
                    nativeAdLayoutBinding.nativeAdView.loadNativeAd(ad = it)
                    adView?.removeAllViews()
                    adView?.addView(nativeAdLayoutBinding.root)
                    adView?.visibility = View.VISIBLE
                }
            }

            dialog.behavior.isDraggable = false
            dialog.setCanceledOnTouchOutside(false)
            videoQualityTv?.setOnClickListener {
                showInterstitialAd {
                    dialog.dismiss()
                    myViewModel.getVideoData(link)
                }
            }
            dialog.show()
        }
    }

    private fun checkIfFileExist(it: VideoModel): Boolean {
        val saveFile = File(rootFile, it.id + ".mp4")
        return saveFile.exists()
    }

    fun showNatAd(): Boolean {
        return remoteConfig.nativeAd
    }

    private fun showNativeAd() {
        if (remoteConfig.nativeAd) {
            nativeAd = googleManager.createNativeAdSmall()
            nativeAd?.let {
                val nativeAdLayoutBinding = NativeAdBannerLayoutBinding.inflate(layoutInflater)
                nativeAdLayoutBinding.nativeAdView.loadNativeAd(ad = it)
                binding.nativeView.removeAllViews()
                binding.nativeView.addView(nativeAdLayoutBinding.root)
                binding.nativeView.visibility = View.VISIBLE
            }
        }
    }

    private fun showInterstitialAd(callback: () -> Unit) {

        val ad: InterstitialAd? =
            googleManager.createInterstitialAd(GoogleInterstitialType.MEDIUM)

        if (ad == null) {
            callback.invoke()
            return
        } else {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    super.onAdDismissedFullScreenContent()
                    callback.invoke()
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    super.onAdFailedToShowFullScreenContent(error)
                    callback.invoke()
                }
            }
            ad.show(activity)
        }

    }

    private fun showDropDown() {
        val nativeAdCheck = googleManager.createNativeFull()
        val nativeAd = googleManager.createNativeFull()
        Log.d("ggg_nul", "nativeAd:${nativeAdCheck}")
        nativeAdCheck?.let {
            Log.d("ggg_lest", "nativeAdEx:${nativeAd}")
            binding.apply {
                dropLayout.bringToFront()
                nativeViewDrop.bringToFront()
            }
            val nativeAdLayoutBinding = MediumNativeAdLayoutBinding.inflate(layoutInflater)
            nativeAdLayoutBinding.nativeAdView.loadNativeAd(ad = it)
            binding.nativeViewDrop.removeAllViews()
            binding.nativeViewDrop.addView(nativeAdLayoutBinding.root)
            binding.nativeViewDrop.visibility = View.VISIBLE
            binding.dropLayout.visibility = View.VISIBLE

            binding.btnDropDown.setOnClickListener {
                binding.dropLayout.visibility = View.GONE
            }
            binding.btnDropUp.visibility = View.INVISIBLE
        }
    }
}