<script lang="ts">
	import Glyph from './Glyph.svelte';

	const faqs = [
		{ q: 'Does it need root?', a: 'No. Everything uses standard Android permissions and WhatsApp’s own notification buttons.' },
		{ q: 'Does it work with the screen off?', a: 'Yes. Ending a WhatsApp call and replying to a chat press the buttons on WhatsApp’s notification, so they work while the phone is locked. Settings like Do Not Disturb and brightness work too.' },
		{ q: 'Why does it ask for so many permissions?', a: 'It doesn’t ask for them all at once. Each permission is asked for only when one of your shortcuts needs it, with a short explanation of what it does and doesn’t do.' },
		{ q: 'I have a Xiaomi, Oppo, Vivo or Samsung phone.', a: 'These phones stop apps that run in the background. Turn on Auto launch (or Autostart) for OpenShortcuts; the app shows you where.' },
		{ q: 'Is it on Google Play?', a: 'Not yet. Download the APK from GitHub Releases, or build it yourself from the source.' }
	];
</script>

<div class="faq card">
	{#each faqs as f (f.q)}
		<details>
			<summary>{f.q}<span class="chev"><Glyph name="chevronDown" size={20} /></span></summary>
			<p>{f.a}</p>
		</details>
	{/each}
</div>

<style>
	.faq {
		margin-top: 32px;
		padding: 4px 24px;
	}
	details + details {
		border-top: 1px solid var(--hairline);
	}
	summary {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 16px;
		min-height: 60px;
		font-size: 1.0625rem;
		font-weight: 600;
		cursor: pointer;
		list-style: none;
		user-select: none;
	}
	summary::-webkit-details-marker {
		display: none;
	}
	.chev {
		color: var(--ink3);
		display: inline-flex;
		flex-shrink: 0;
		transition: rotate 220ms var(--ease-out);
	}
	details[open] .chev {
		rotate: 180deg;
	}
	p {
		color: var(--ink2);
		padding-bottom: 18px;
		max-width: 44em;
	}
</style>
