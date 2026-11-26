<script lang="ts">
	import Button from './Button.svelte';
	import Phone from './Phone.svelte';
	import { APK, REPO } from '#lib/site.ts';
</script>

<section class="hero wrap">
	<h1>The Shortcuts app Android was missing.</h1>
	<p class="sub">
		Build routines from simple sentences. Run them with one tap, or let them run on their own.
	</p>
	<div class="ctas">
		<Button href={APK}>Download APK</Button>
		<Button href={REPO} variant="outlined">View on GitHub</Button>
	</div>
	<p class="fine">Free · Open source · No root · Android 10+</p>

	<div class="phones" aria-hidden="false">
		<div class="back"><Phone src="gallery" alt="The Gallery of ready-made shortcuts" eager /></div>
		<div class="front"><Phone src="editor" alt="The Sleep Mode shortcut: pause media, end the WhatsApp call, turn on Do Not Disturb, dim the screen" eager /></div>
	</div>
</section>

<style>
	.hero {
		text-align: center;
		padding-top: clamp(40px, 7vw, 72px);
	}
	h1 {
		font-size: clamp(2.375rem, 6.4vw, 4.25rem);
		font-weight: 700;
		max-width: 13em;
		margin-inline: auto;
	}
	.sub {
		font-size: clamp(1.125rem, 2vw, 1.375rem);
		color: var(--ink2);
		max-width: 30em;
		margin: 18px auto 0;
	}
	.ctas {
		display: flex;
		flex-wrap: wrap;
		justify-content: center;
		gap: 12px;
		margin-top: 28px;
	}
	.fine {
		margin-top: 16px;
		font-size: 0.875rem;
		color: var(--ink3);
	}
	.phones {
		position: relative;
		width: min(520px, 92%);
		height: clamp(420px, 62vw, 620px);
		margin: 48px auto 0;
		overflow: hidden;
		mask-image: linear-gradient(to bottom, black 78%, transparent);
		-webkit-mask-image: linear-gradient(to bottom, black 78%, transparent);
	}
	.phones > div {
		position: absolute;
		top: 0;
		width: 56%;
	}
	.back {
		left: 4%;
		top: 40px !important;
		rotate: -7deg;
		opacity: 0.92;
	}
	.front {
		right: 4%;
		rotate: 5deg;
	}
</style>
