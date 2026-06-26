package forpdateam.ru.forpda.ui.views.messagepanel.advanced

import android.annotation.SuppressLint
import android.content.Context
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.ui.views.messagepanel.MessagePanel
import forpdateam.ru.forpda.ui.views.messagepanel.advanced.adapters.PanelItemAdapter

/**
 * Created by radiationx on 08.01.17.
 */
@SuppressLint("ViewConstructor")
class SmilesPanelItem(context: Context, panel: MessagePanel) :
    BasePanelItem(context, panel, context.getString(R.string.smiles_title)) {

    companion object {
        private var smiles: MutableList<ButtonData>? = null
        private var urlToAssets: MutableList<String>? = null

        @JvmStatic
        fun getSmiles(): List<ButtonData> {
            smiles?.let { return it }
            val list = mutableListOf<ButtonData>()
            smiles = list
            list.apply {
                add(ButtonData(":happy:", "happy.gif"))
                add(ButtonData(";)", "wink.gif"))
                add(ButtonData(":P", "tongue.gif"))
                add(ButtonData(":-D", "biggrin.gif"))
                add(ButtonData(":lol:", "laugh.gif"))
                add(ButtonData(":rolleyes:", "rolleyes.gif"))
                add(ButtonData(":)", "smile_good.gif"))
                add(ButtonData(":beee:", "beee.gif"))
                add(ButtonData(":rofl:", "rofl.gif"))
                add(ButtonData(":sveta:", "sveta.gif"))
                add(ButtonData(":thank_you:", "thank_you.gif"))
                add(ButtonData("}-)", "devil.gif"))
                add(ButtonData(":girl_cray:", "girl_cray.gif"))
                add(ButtonData(":blush:", "blush.gif"))
                add(ButtonData(":mellow:", "mellow.gif"))
                add(ButtonData(":huh:", "huh.gif"))
                add(ButtonData("B)", "cool.gif"))
                add(ButtonData("-_-", "sleep.gif"))
                add(ButtonData("&lt;_&lt;", "dry.gif"))
                add(ButtonData(":wub:", "wub.gif"))
                add(ButtonData(":angry:", "angry.gif"))
                add(ButtonData(":(", "sad.gif"))
                add(ButtonData(":unsure:", "unsure.gif"))
                add(ButtonData(":wacko:", "wacko.gif"))
                add(ButtonData(":blink:", "blink.gif"))
                add(ButtonData(":ph34r:", "ph34r.gif"))
                add(ButtonData(":banned:", "banned.gif"))
                add(ButtonData(":antifeminism:", "antifeminism.gif"))
                add(ButtonData(":beta:", "beta.gif"))
                add(ButtonData(":boy_girl:", "boy_girl.gif"))
                add(ButtonData(":butcher:", "butcher.gif"))
                add(ButtonData(":bubble:", "bubble.gif"))
                add(ButtonData(":censored:", "censored.gif"))
                add(ButtonData(":clap:", "clap.gif"))
                add(ButtonData(":close_tema:", "close_tema.gif"))
                add(ButtonData(":clapping:", "clapping.gif"))
                add(ButtonData(":coldly:", "coldly.gif"))
                add(ButtonData(":comando:", "comando.gif"))
                add(ButtonData(":dance:", "dance.gif"))
                add(ButtonData(":daisy:", "daisy.gif"))
                add(ButtonData(":dancer:", "dancer.gif"))
                add(ButtonData(":derisive:", "derisive.gif"))
                add(ButtonData(":dinamo:", "dinamo.gif"))
                add(ButtonData(":dirol:", "dirol.gif"))
                add(ButtonData(":diver:", "diver.gif"))
                add(ButtonData(":drag:", "drag.gif"))
                add(ButtonData(":download:", "download.gif"))
                add(ButtonData(":drinks:", "drinks.gif"))
                add(ButtonData(":first_move:", "first_move.gif"))
                add(ButtonData(":feminist:", "feminist.gif"))
                add(ButtonData(":flood:", "flood.gif"))
                add(ButtonData(":fool:", "fool.gif"))
                add(ButtonData(":friends:", "friends.gif"))
                add(ButtonData(":foto:", "foto.gif"))
                add(ButtonData(":girl_blum:", "girl_blum.gif"))
                add(ButtonData(":girl_crazy:", "girl_crazy.gif"))
                add(ButtonData(":girl_curtsey:", "girl_curtsey.gif"))
                add(ButtonData(":girl_dance:", "girl_dance.gif"))
                add(ButtonData(":girl_flirt:", "girl_flirt.gif"))
                add(ButtonData(":girl_hospital:", "girl_hospital.gif"))
                add(ButtonData(":girl_hysterics:", "girl_hysterics.gif"))
                add(ButtonData(":girl_in_love:", "girl_in_love.gif"))
                add(ButtonData(":girl_kiss:", "girl_kiss.gif"))
                add(ButtonData(":girl_pinkglassesf:", "girl_pinkglassesf.gif"))
                add(ButtonData(":girl_parting:", "girl_parting.gif"))
                add(ButtonData(":girl_prepare_fish:", "girl_prepare_fish.gif"))
                add(ButtonData(":good:", "good.gif"))
                add(ButtonData(":girl_spruce_up:", "girl_spruce_up.gif"))
                add(ButtonData(":girl_tear:", "girl_tear.gif"))
                add(ButtonData(":girl_tender:", "girl_tender.gif"))
                add(ButtonData(":girl_teddy:", "girl_teddy.gif"))
                add(ButtonData(":girl_to_babruysk:", "girl_to_babruysk.gif"))
                add(ButtonData(":girl_to_take_umbrage:", "girl_to_take_umbrage.gif"))
                add(ButtonData(":girl_triniti:", "girl_triniti.gif"))
                add(ButtonData(":girl_tongue:", "girl_tongue.gif"))
                add(ButtonData(":girl_wacko:", "girl_wacko.gif"))
                add(ButtonData(":girl_werewolf:", "girl_werewolf.gif"))
                add(ButtonData(":girl_witch:", "girl_witch.gif"))
                add(ButtonData(":grabli:", "grabli.gif"))
                add(ButtonData(":good_luck:", "good_luck.gif"))
                add(ButtonData(":guess:", "guess.gif"))
                add(ButtonData(":hang:", "hang.gif"))
                add(ButtonData(":heart:", "heart.gif"))
                add(ButtonData(":help:", "help.gif"))
                add(ButtonData(":helpsmilie:", "helpsmilie.gif"))
                add(ButtonData(":hemp:", "hemp.gif"))
                add(ButtonData(":heppy_dancing:", "heppy_dancing.gif"))
                add(ButtonData(":hysterics:", "hysterics.gif"))
                add(ButtonData(":indeec:", "indeec.gif"))
                add(ButtonData(":i-m_so_happy:", "i-m_so_happy.gif"))
                add(ButtonData(":kindness:", "kindness.gif"))
                add(ButtonData(":king:", "king.gif"))
                add(ButtonData(":laugh_wild:", "laugh_wild.gif"))
                add(ButtonData(":4PDA:", "love_4PDA.gif"))
                add(ButtonData(":nea:", "nea.gif"))
                add(ButtonData(":moil:", "moil.gif"))
                add(ButtonData(":no:", "no.gif"))
                add(ButtonData(":nono:", "nono.gif"))
                add(ButtonData(":offtopic:", "offtopic.gif"))
                add(ButtonData(":ok:", "ok.gif"))
                add(ButtonData(":papuas:", "papuas.gif"))
                add(ButtonData(":party:", "party.gif"))
                add(ButtonData(":pioneer_smoke:", "pioneer_smoke.gif"))
                add(ButtonData(":pipiska:", "pipiska.gif"))
                add(ButtonData(":protest:", "protest.gif"))
                add(ButtonData(":popcorm:", "popcorm.gif"))
                add(ButtonData(":rabbi:", "rabbi.gif"))
                add(ButtonData(":resent:", "resent.gif"))
                add(ButtonData(":roll:", "roll.gif"))
                add(ButtonData(":rtfm:", "rtfm.gif"))
                add(ButtonData(":russian_garmoshka:", "russian_garmoshka.gif"))
                add(ButtonData(":russian:", "russian.gif"))
                add(ButtonData(":russian_ru:", "russian_ru.gif"))
                add(ButtonData(":scratch_one-s_head:", "scratch_one-s_head.gif"))
                add(ButtonData(":scare:", "scare.gif"))
                add(ButtonData(":search:", "search.gif"))
                add(ButtonData(":secret:", "secret.gif"))
                add(ButtonData(":skull:", "skull.gif"))
                add(ButtonData(":shok:", "shok.gif"))
                add(ButtonData(":sorry:", "sorry.gif"))
                add(ButtonData(":smoke:", "smoke.gif"))
                add(ButtonData(":spiteful:", "spiteful.gif"))
                add(ButtonData(":stop_flood:", "stop_flood.gif"))
                add(ButtonData(":suicide:", "suicide.gif"))
                add(ButtonData(":stop_holywar:", "stop_holywar.gif"))
                add(ButtonData(":superman:", "superman.gif"))
                add(ButtonData(":superstition:", "superstition.gif"))
                add(ButtonData(":tablet_za:", "tablet_protiv.gif"))
                add(ButtonData(":tablet_protiv:", "tablet_za.gif"))
                add(ButtonData(":this:", "this.gif"))
                add(ButtonData(":tomato:", "tomato.gif"))
                add(ButtonData(":to_clue:", "to_clue.gif"))
                add(ButtonData(":tommy:", "tommy.gif"))
                add(ButtonData(":tongue3:", "tongue3.gif"))
                add(ButtonData(":umnik:", "umnik.gif"))
                add(ButtonData(":victory:", "victory.gif"))
                add(ButtonData(":vinsent:", "vinsent.gif"))
                add(ButtonData(":wallbash:", "wallbash.gif"))
                add(ButtonData(":whistle:", "whistle.gif"))
                add(ButtonData(":wink_kind:", "wink_kind.gif"))
                add(ButtonData(":yahoo:", "yahoo.gif"))
                add(ButtonData(":yes:", "yes.gif"))
                add(ButtonData(":&#91;", "confusion.gif"))
                add(ButtonData("&#93;-:{", "girl_devil.gif"))
                add(ButtonData(":*", "kiss.gif"))
                add(ButtonData("@}-'-,-", "give_rose.gif"))
                add(ButtonData(":'(", "cry.gif"))
                add(ButtonData(":-{", "mad.gif"))
                add(ButtonData("=^.^=", "kitten.gif"))
                add(ButtonData("(-=", "girl_hide.gif"))
                add(ButtonData("(-;", "girl_wink.gif"))
                add(ButtonData(")-:{", "girl_angry.gif"))
                add(ButtonData("*-:", "girl_chmok.gif"))
                add(ButtonData(")-:", "girl_sad.gif"))
                add(ButtonData(":girl_mad:", "girl_mad.gif"))
                add(ButtonData("(-:", "girl_smile.gif"))
                add(ButtonData(":acute:", "acute.gif"))
                add(ButtonData(":aggressive:", "aggressive.gif"))
                add(ButtonData(":air_kiss:", "air_kiss.gif"))
                add(ButtonData(":lol_girl:", "girl_haha.gif"))
                add(ButtonData(":ohmy:", "ohmy.gif"))
                add(ButtonData(":smile:", "smile.gif"))
            }
            return list
        }

        @JvmStatic
        fun getUrlToAssets(): List<String> {
            urlToAssets?.let { return it }
            val list = mutableListOf<String>()
            for (data in getSmiles()) {
                list.add("assets://smiles/${data.icon}")
            }
            urlToAssets = list
            return list
        }
    }

    init {
        val adapter = PanelItemAdapter(getSmiles().toMutableList(), getUrlToAssets(), PanelItemAdapter.TYPE_ASSET)
        adapter.setOnItemClickListener(object : PanelItemAdapter.OnItemClickListener {
            override fun onItemClick(item: ButtonData) {
                messagePanel.insertText(" ${item.text} ")
            }
        })
        recyclerView.adapter = adapter
    }
}
