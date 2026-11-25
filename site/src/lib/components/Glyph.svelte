<script lang="ts">
	import glyphs from '#lib/glyphs.json';

	let { name, size = 22, label }: { name: string; size?: number; label?: string } = $props();

	const strokes = glyphs.strokes as Record<string, string[]>;
	const fills = glyphs.fills as Record<string, string>;
	let fill = $derived(fills[name]);
	let paths = $derived(strokes[name] ?? strokes.layers);
</script>

<svg
	width={size}
	height={size}
	viewBox="0 0 24 24"
	role={label ? 'img' : undefined}
	aria-label={label}
	aria-hidden={label ? undefined : 'true'}
>
	{#if fill}
		<path d={fill} fill="currentColor" />
	{:else}
		{#each paths as d (d)}
			<path {d} fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
		{/each}
	{/if}
</svg>
