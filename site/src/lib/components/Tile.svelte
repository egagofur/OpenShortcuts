<script lang="ts">
	import Glyph from './Glyph.svelte';
	import type { Tint } from '#lib/site.ts';

	let { glyph, tint, size = 40 }: { glyph: string; tint: Tint; size?: number } = $props();
</script>

<span class="tile" style:--t="var(--tint-{tint})" style:--s="{size}px">
	<Glyph name={glyph} size={Math.round(size * 0.58)} />
</span>

<style>
	.tile {
		display: inline-grid;
		place-items: center;
		flex-shrink: 0;
		width: var(--s);
		height: var(--s);
		border-radius: calc(var(--s) * 0.28);
		background: var(--t);
		color: white;
	}
</style>
