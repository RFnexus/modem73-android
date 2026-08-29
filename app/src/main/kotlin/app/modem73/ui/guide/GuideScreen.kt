package app.modem73.ui.guide

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.modem73.ui.components.SectionTitle
import app.modem73.ui.theme.Modem73Colors

private data class GuideEntry(val term: String?, val text: String)

private val guideEntries = listOf(
    GuideEntry(null, "BASICS"),
    GuideEntry("SNR", "Signal to noise ratio. This is how far your signal sits above the noise floor in dB. A higher SNR is always better and combined with bit error rate determines link quality."),
    GuideEntry("Bit error rate", "The total percentage of raw bit errors before forward error correction. modem73 works it out by re-encoding the frame once it decodes, then counting how many of the received bits disagreed. A frame can come through perfectly clean at 15% BER, because fixing those errors is the entire job of the FEC. What BER tells you is how much margin is left before frames start failing."),
    GuideEntry("Frame size", "How many bytes go out in one transmission. OFDM frames run from 256 to 6144 bytes depending on modulation and code rate (SHORT, NORMAL or LONG), ROBUST frames are 510, 170 or speciality modes like RDM-QB, and the MODEM INFO section shows the exact number for the mode you have picked. A bigger frame wastes less time on sync and overhead but spends longer on air, and one deep fade can take the whole frame with it. Longer frames are less of an issue on line of sight FM. Packets bigger than the frame are split up by fragmentation when enabled."),
    GuideEntry("Modulation", "What carrier, number of carriers, and how many bits we send at once. Higher carriers (like QAM4096) require a better signal, or SNR. Lower modulation orders like BPSK require a lot less."),
    GuideEntry("Mode", "modem73 has 3 modes: OFDM, ROBUST, or MFSK."),
    GuideEntry("OFDM", "The fast family. Hundreds of carriers side by side in 2400 Hz, each carrying a PSK or QAM symbol. From about 790 bps at BPSK to over 13 kbps at QAM4096. Use it for anything over FM, and on good HF SSB paths at 8PSK or below."),
    GuideEntry("ROBUST", "Built for fading HF such as 40 and 80 meter NVIS. QPSK on widely spaced carriers with a guard interval between symbols, so Doppler spread and multipath echoes do not smear one symbol into the next. RDM-1200 (about 1150 bps) decodes down to 5 dB SNR and RDM-600 near 0 dB. The RDMN modes are 600 Hz wide versions, RDMN-300 and RDMN-150, for narrow filters and crowded bands."),
    GuideEntry("MFSK", "One tone at a time out of 8, 16 or 32. The receiver only has to find the loudest tone, with no phase tracking, which is why it decodes below the noise floor (MFSK-8 to about -9 dB) and why it is slow: 34 bps for MFSK-8, 99 bps for MFSK-32R. Keep it as the weak signal backup."),
    GuideEntry("How do I pick a mode?", "Start with the lowest mode first. Then, go up. Don't pick something like QAM256 right out of the box. Check your SNR and BER and step up one notch at a time while frames keep decoding. BER is the number to watch: low means you have margin to go faster, climbing means you are near the edge and the next step up will start dropping frames. SNR tells you roughly where you will land. modem73 shows it green above 10 dB and yellow between 5 and 10, and the higher modulations need the green. When frames start failing, step back down one notch and stay there.\n\nIf you're on HF, start with the ROBUST modes. RDM-600 while the band is fading, RDM-1200 once it decodes cleanly with SNR over 5 dB. Only move to OFDM when the path is steady, and keep it at 8PSK or below. If nothing decodes at all, drop to MFSK.\n\nRemember that two stations can use different settings, and as long as they have the RX decoder on, can hear you. This enables setups with asymmetric conditions.\n\nYou should always make sure your audio input and output are tuned properly. Use the TX level and check to see if your packets are distorted or overmodulated. If you use Hamlib or Rigctl PTT, there is an auto-ALC tune feature under RIG."),
    GuideEntry(null, "MODEM SETTINGS"),
    GuideEntry("Code rate", "How much of the frame is data and how much is error correction. 5/6 is almost all data and needs a clean channel. 1/4 spends three quarters of the frame on correction and decodes deep in the noise."),
    GuideEntry("Postamble", "A second sync marker at the end of an OFDM frame. If the receiver missed the start, it can still lock on at the end and recover the frame. Costs 0.4 s of airtime. Worth it on noisy or fading channels."),
    GuideEntry("RDM mode", "Which ROBUST speed to send. Lower numbers are slower and decode at a lower SNR."),
    GuideEntry("MFSK mode", "How many tones. More tones means more bits per symbol and a wider signal: MFSK-8 is 250 Hz wide, MFSK-32 is 1000 Hz. 32R keeps 32 tones with less error correction for more speed."),
    GuideEntry("RX decoders", "Which families the receiver listens for. The receiver decodes all three at once by default. Each one costs CPU, so on an older phone turn off the ones you are not using."),
    GuideEntry(null, "SIGNAL"),
    GuideEntry("Level", "How loud the audio coming into the sound card is, in dB below full scale. 0 dB is the loudest the sound card can take. THRESHOLD CSMA compares this to Threshold to decide if the channel is busy."),
    GuideEntry("Threshold", "The Level above which THRESHOLD CSMA calls the channel busy. Set it a few dB above your normal noise floor."),
    GuideEntry("Waterfall", "The audio spectrum over time. Your signal should sit in the middle of the passband with nothing else on top of it."),
    GuideEntry(null, "CSMA"),
    GuideEntry("CSMA", "Listen before transmit, so stations do not talk over each other. With it off, modem73 keys up as soon as a packet is queued."),
    GuideEntry("Mode (CSMA)", "THRESHOLD calls the channel busy when any audio is over Threshold. SYNC only counts a real modem73 signal, so HF noise cannot hold you off forever. RANKED is SYNC plus stations taking turns in a fixed order; every station must run 2.3 or newer with RANKED on."),
    GuideEntry("Band / Preset", "Timing presets. Band picks HF or VHF/UHF numbers and Preset picks how busy the channel is. The knobs below are filled in from these; changing one by hand overrides it."),
    GuideEntry("Quiet", "How long the channel has to be idle before you contend for it."),
    GuideEntry("Window", "The random wait drawn after Quiet, so two stations that are ready at the same moment do not collide."),
    GuideEntry("Lead tone", "A short tone at keyup so other stations hear you before the data starts. Always on when RANKED is enabled."),
    GuideEntry("Reply offset", "A small per-callsign delay so replies from several stations do not land on the same instant."),
    GuideEntry("Burst", "How many queued packets you send once you win the channel."),
    GuideEntry("Fast floor", "Shorter waits in SYNC mode. Only if every station runs 2.3 or newer."),
    GuideEntry("Presence interval", "In RANKED, an idle station sends a presence tone every 45 to 90 s so the others keep it in the turn order."),
    GuideEntry(null, "TX AND RX"),
    GuideEntry("Fragmentation", "Splits packets bigger than one frame into pieces and reassembles them at the far end. Turn it on whenever another program talks to modem73 over KISS. Recommended to turn on when you're receiving packets from applications that may exceed your frame size."),
    GuideEntry("TX blanking", "Mutes the decoder while you transmit so you do not decode your own signal through the mic."),
    GuideEntry("TX delay", "Time between keying PTT and the start of audio, 250 to 2500 ms, so the radio is fully on transmit before the data starts."),
    GuideEntry("TX level", "Sound card output drive, 5 to 100 percent. Set it so the radio's ALC barely moves. Too hot distorts the signal and other stations decode less, not more."),
    GuideEntry("PTT", "How modem73 keys the radio. NONE: no keying, speaker into mic. RIGCTL: through rigctld over TCP. VOX: a tone before the data trips the radio's VOX. SERIAL: DTR or RTS on a serial port, which is what the AIOC uses. CM108: the GPIO pin on a CM108 USB sound card. HAMLIB: direct Hamlib control without rigctld."),
    GuideEntry("VOX tone / lead / tail", "For VOX PTT: the tone frequency, how long it plays before the data so the radio keys up, and how long after so the radio does not drop early."),
    GuideEntry(null, "NETWORK"),
    GuideEntry("Callsign", "Goes in every frame header so other stations can see who transmitted. Also drives the CSMA dither and the RANKED turn order."),
    GuideEntry("KISS port", "TCP port, 8001 by default, where applications send and receive packets."),
    GuideEntry("Control port", "TCP port, 8073 by default, for the JSON control API: read SNR and channel state, change modes, or pass commands through to rigctl."),
    GuideEntry("LAN access", "Listen on every network interface instead of only localhost, so other machines on your LAN can use the modem.")
)

@Composable
fun GuideScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    LazyColumn(Modifier.fillMaxSize()) {
        items(guideEntries) { e ->
            if (e.term == null) {
                SectionTitle(e.text, color = Modem73Colors.info)
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = e.term,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = e.text,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
