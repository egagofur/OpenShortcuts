<script lang="ts">
	import Tile from './Tile.svelte';
	import { cubicOut } from 'svelte/easing';
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

	const still = typeof matchMedia !== 'undefined' && matchMedia('(prefers-reduced-motion: reduce)').matches;
	function swap(_: Element, { duration }: { duration: number }) {
		return {
			duration,
			easing: cubicOut,
			css: (t: number) =>
				still ? `opacity:${t}` : `opacity:${t};translate:0 ${(1 - t) * 6}px;filter:blur(${(1 - t) * 2}px)`
		};
	}
</script>

<div class="sentence card">
	<p class="label">Automation</p>
	<div class="stack">
	{#key i}
		<div class="ex" in:swap={{ duration: 260 }} out:swap={{ duration: 160 }}>
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
		</div>
	{/key}
	</div>
	<p class="hint">Select a blue word to see another automation.</p>
</div>

<style>
	.sentence {
		padding: clamp(20px, 3vw, 32px);
		max-width: 680px;
		margin-inline: auto;
		text-align: left;
	}
	.label {
		font-size: 0.8125rem;
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.05em;
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
	}
	.stack {
		display: grid;
	}
	.ex {
		grid-area: 1 / 1;
	}
	.run {
		margin-top: 10px;
	}
	.text {
		font-size: clamp(1.1875rem, 2.4vw, 1.5rem);
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
		position: relative;
		cursor: pointer;
		touch-action: manipulation;
		transition: scale 140ms var(--ease-out);
	}
	.token::before {
		content: '';
		position: absolute;
		inset: -9px -2px;
	}
	.token:active {
		scale: 0.97;
	}
	.hint {
		margin-top: 14px;
		font-size: 0.875rem;
		color: var(--ink3);
	}
	@media (prefers-reduced-motion: reduce) {
		.token:active {
			scale: none;
		}
	}
</style>
