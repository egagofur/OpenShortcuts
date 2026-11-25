<script lang="ts">
	import { ISSUES, RELEASES, REPO } from '#lib/site.ts';
</script>

<footer class="wrap">
	<p>OpenShortcuts is free software under the MIT License. Not affiliated with Apple or WhatsApp.</p>
	<ul>
		<li><a href={REPO} rel="noopener">GitHub</a></li>
		<li><a href={RELEASES} rel="noopener">Releases</a></li>
		<li><a href={ISSUES} rel="noopener">Report a bug</a></li>
	</ul>
</footer>

<style>
	footer {
		display: flex;
		flex-wrap: wrap;
		justify-content: space-between;
		gap: 16px;
		padding-block: 32px 48px;
		margin-top: var(--section-gap);
		border-top: 1px solid var(--hairline);
		font-size: 14px;
		color: var(--ink3);
	}
	ul {
		display: flex;
		gap: 20px;
		list-style: none;
		margin: 0;
		padding: 0;
	}
	a {
		color: var(--ink2);
		text-decoration: none;
	}
	@media (hover: hover) and (pointer: fine) {
		a:hover {
			color: var(--ink);
		}
	}
</style>
