<script lang="ts">
	import Nav from '#lib/components/Nav.svelte';
	import Hero from '#lib/components/Hero.svelte';
	import SentenceCard from '#lib/components/SentenceCard.svelte';
	import Split from '#lib/components/Split.svelte';
	import TriggerGrid from '#lib/components/TriggerGrid.svelte';
	import Cards from '#lib/components/Cards.svelte';
	import ActionChips from '#lib/components/ActionChips.svelte';
	import Faq from '#lib/components/Faq.svelte';
	import Footer from '#lib/components/Footer.svelte';
	import Button from '#lib/components/Button.svelte';
	import { asset, resolve } from '$app/paths';
	import { reveal } from '#lib/reveal.ts';
	import { APK, REPO } from '#lib/site.ts';
</script>

<Nav />

<main>
	<Hero />

	<section class="section wrap story">
		<div use:reveal>
			<p class="eyebrow">How it started</p>
			<h2 class="h2">Falling asleep on a call shouldn’t cost you a battery.</h2>
			<p class="lede center">
				OpenShortcuts began with one automation: end the WhatsApp call once you’re asleep, then turn on Do Not Disturb and
				dim the screen. Now it does that, and whatever else you build.
			</p>
		</div>
		<div class="sentence-wrap" use:reveal={80}><SentenceCard /></div>
	</section>

	<Split id="features" eyebrow="Reads like a sentence" title="Tap a blue word to change it." shot="editor" alt="The Sleep Mode shortcut in the editor">
		<p class="lede">
			Every action is a short sentence: “Turn <b>On</b> Do Not Disturb”, “Set brightness to <b>10%</b>”. No flowcharts and no
			scripting language. Pick people, apps and times from real pickers.
		</p>
		<p class="lede">Start from a blank shortcut or from twelve in the Gallery, like Sleep Mode, Pomodoro or Low Battery Saver.</p>
	</Split>

	<section id="triggers" class="section wrap">
		<div use:reveal>
			<p class="eyebrow">Runs on its own · 15 triggers</p>
			<h2 class="h2">From sunset to your car’s Bluetooth.</h2>
			<p class="lede">
				From the time of day and sunset to arriving home, your car’s Bluetooth or a message from someone. Each automation
				shows exactly when it runs next, and runs without a notice to approve.
			</p>
		</div>
		<TriggerGrid />
	</section>

	<Split eyebrow="Down to the minute" title="Sunrise, sunset and every hour in between." shot="sunset" alt="A sunset trigger set to 15 minutes before sunset" flip>
		<p class="lede">
			Run at a time, at sunrise or sunset with an offset, every 15 minutes to 12 hours within the hours you choose, or once
			on a date. Turn on “Ask before running” when you want a minute to skip it.
		</p>
	</Split>

	<section class="section wrap">
		<div use:reveal>
			<p class="eyebrow">Works while your phone is locked</p>
			<h2 class="h2">Asleep, locked, screen off. Still works.</h2>
			<p class="lede">It goes through WhatsApp’s own notification buttons, so nothing has to unlock or open.</p>
		</div>
		<Cards
			items={[
				{ glyph: 'phoneOff', tint: 'green', title: 'End a WhatsApp call', body: 'Presses Hang up on the call notification after the minutes you set.' },
				{ glyph: 'quote', tint: 'teal', title: 'Reply to a chat', body: 'Answers from the notification’s Reply box, right from the lock screen.' },
				{ glyph: 'send', tint: 'blue', title: 'Send a message', body: 'Sends while Smart Lock keeps the phone open at home, or the moment you unlock.' }
			]}
		/>
	</section>

	<section class="section wrap">
		<div use:reveal>
			<p class="eyebrow">60+ actions</p>
			<h2 class="h2">Everything a routine needs.</h2>
		</div>
		<ActionChips />
	</section>

	<section class="section wrap design">
		<div use:reveal>
			<p class="eyebrow">Designed like a phone’s own app</p>
			<h2 class="h2">Smooth, quiet and yours.</h2>
			<p class="lede center">
				White sentence cards, a floating sheet, screens that slide like iOS, haptics and dark mode. In landscape, a rail
				with the clock and a battery ring you can tap.
			</p>
		</div>
		<div class="landscape" use:reveal={80}>
			<img
				src={asset('shots/landscape.webp')}
				alt="OpenShortcuts in landscape: the rail with the clock and battery ring next to the shortcut editor"
				width="1600"
				height="728"
				loading="lazy"
				decoding="async"
			/>
		</div>
	</section>

	<section id="privacy" class="section wrap">
		<div use:reveal>
			<p class="eyebrow">Private by design</p>
			<h2 class="h2">Nothing leaves your phone.</h2>
			<p class="lede">
				Permissions are asked for one at a time, only when a shortcut needs them, and each one says what it does and what it
				doesn’t.
			</p>
		</div>
		<Cards
			items={[
				{ glyph: 'person', tint: 'gray', title: 'No account', body: 'Install it and start. No sign-up, no email, no tracking.' },
				{ glyph: 'lock', tint: 'indigo', title: 'Runs on the phone', body: 'Shortcuts are stored on your phone. The network is used only for map tiles, and for Describe a shortcut if you add your own Claude key.' },
				{ glyph: 'braces', tint: 'orange', title: 'Open source', body: 'MIT licensed. Read every line, build it yourself, or help improve it.' }
			]}
		/>
	</section>

	<section class="section wrap">
		<div use:reveal>
			<p class="eyebrow">Questions</p>
			<h2 class="h2">Good to know.</h2>
		</div>
		<Faq />
	</section>

	<section class="section wrap final" use:reveal>
		<img src={asset('icon.svg')} alt="" width="72" height="72" />
		<h2 class="h2">Make your first shortcut tonight.</h2>
		<p class="lede center">Free for Android 10 and later. No root needed.</p>
		<div class="ctas">
			<Button href={APK}>Download APK</Button>
			<Button href={REPO} variant="outlined">Star on GitHub</Button>
		</div>
	</section>
</main>

<Footer />

<style>
	.story,
	.design,
	.final {
		text-align: center;
	}
	.center {
		margin-inline: auto;
	}
	.sentence-wrap {
		margin-top: 40px;
	}
	.landscape {
		margin-top: 40px;
		padding: 8px;
		border-radius: 36px;
		background: #1a1a1d;
		box-shadow:
			inset 0 0 0 1.5px #3a3a40,
			0 30px 60px -20px rgb(0 0 0 / 0.35);
	}
	.landscape img {
		border-radius: 28px;
		width: 100%;
	}
	.final img {
		margin: 0 auto 20px;
		border-radius: 16px;
	}
	.ctas {
		display: flex;
		flex-wrap: wrap;
		justify-content: center;
		gap: 12px;
		margin-top: 28px;
	}
	.lede :global(b) {
		color: var(--accent);
		font-weight: 600;
	}
</style>
