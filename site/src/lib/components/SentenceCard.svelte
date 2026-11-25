<script lang="ts">
	import Tile from './Tile.svelte';
	import type { Tint } from '#lib/site.ts';

	type Part = string | { token: string };
	type Example = { glyph: string; tint: Tint; parts: Part[]; run: string };

	const examples: Example[] = [
		{ glyph: 'phone', tint: 'green', parts: ['When a ', { token: 'WhatsApp call' }, ' runs over ', { token: '45 min' }, ' between ', { token: '23:00' }, ' and ', { token: '05:00' }], run: 'Sleep Mode' },
		{ glyph: 'pin', tint: 'red', parts: ['When I ', { token: 'arrive' }, ' at ', { token: 'Home' }, ' within ', { token: '150 m' }], run: 'Good Evening' },
		{ glyph: 'sun', tint: 'orange', parts: [{ token: '15 min before' }, ' sunset, ', { token: 'daily' }], run: 'Warm Lights' },
		{ glyph: 'bluetooth', tint: 'blue', parts: ['When ', { token: 'My Car' }, ' connects'], run: 'Drive Mode' },
		{ glyph: 'quote', tint: 'teal', parts: ['When a WhatsApp message from ', { token: 'Mom' }, ' arrives'], run: 'Urgent Alert' }
	];

	let i = $state(0);
	let ex = $derived(examples[i]);
	const next = () => (i = (i + 1) % examples.length);
</script>

<div class="sentence card">
	<p class="label">Automation</p>
	{#key i}
		<div class="row">
			<Tile glyph={ex.glyph} tint={ex.tint} size={44} />
			<p class="text">
				{#each ex.parts as p, k (k)}
					{#if typeof p === 'string'}{p}{:else}<button type="button" class="token" onclick={next}>{p.token}</button>{/if}
				{/each}
			</p>
		</div>
		<div class="row run">
			<Tile glyph="layers" tint="indigo" size={44} />
			<p class="text">Run <button type="button" class="token" onclick={next}>{ex.run}</button></p>
		</div>
	{/key}
	<p class="hint">Tap a blue word to see another automation.</p>
</div>

<style>
	.sentence {
		padding: clamp(20px, 3vw, 32px);
		max-width: 680px;
		margin-inline: auto;
		text-align: left;
	}
	.label {
		font-size: 13px;
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.04em;
		color: var(--ink3);
		margin-bottom: 14px;
	}
	.row {
		display: flex;
		align-items: flex-start;
		gap: 14px;
		padding: 14px;
		border-radius: 18px;
		background: var(--bg);
		animation: settle 420ms var(--ease-out);
	}
	.run {
		margin-top: 10px;
		animation-delay: 60ms;
		animation-fill-mode: backwards;
	}
	.text {
		font-size: clamp(19px, 2.4vw, 24px);
		font-weight: 600;
		line-height: 1.45;
		letter-spacing: -0.01em;
		padding-top: 4px;
	}
	.token {
		font: inherit;
		color: var(--accent);
		background: var(--accent-wash);
		border: 0;
		border-radius: 10px;
		padding: 0 8px;
		margin: 0 1px;
		cursor: pointer;
		touch-action: manipulation;
		transition: scale 140ms var(--ease-out);
	}
	.token:active {
		scale: 0.95;
	}
	.hint {
		margin-top: 14px;
		font-size: 14px;
		color: var(--ink3);
	}
	@keyframes settle {
		from {
			opacity: 0;
			translate: 0 6px;
		}
	}
	@media (prefers-reduced-motion: reduce) {
		.row {
			animation: none;
		}
		.token:active {
			scale: none;
		}
	}
</style>
