<script lang="ts">
	import Tile from './Tile.svelte';
	import { reveal } from '#lib/reveal.ts';
	import type { Tint } from '#lib/site.ts';

	let { items }: { items: { glyph: string; tint: Tint; title: string; body: string }[] } = $props();
</script>

<div class="cards">
	{#each items as it, i (it.title)}
		<article class="card" use:reveal={i * 60}>
			<Tile glyph={it.glyph} tint={it.tint} size={40} />
			<h3>{it.title}</h3>
			<p>{it.body}</p>
		</article>
	{/each}
</div>

<style>
	.cards {
		display: grid;
		grid-template-columns: repeat(3, minmax(0, 1fr));
		gap: 16px;
		margin-top: 40px;
	}
	article {
		padding: 24px;
	}
	h3 {
		font-size: 19px;
		font-weight: 650;
		letter-spacing: -0.01em;
		margin-top: 16px;
	}
	p {
		color: var(--ink2);
		margin-top: 6px;
		font-size: 16px;
	}
	@media (max-width: 860px) {
		.cards {
			grid-template-columns: 1fr;
		}
	}
</style>
