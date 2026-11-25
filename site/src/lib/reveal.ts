export function reveal(node: HTMLElement, delay = 0) {
	node.classList.add('reveal');
	node.style.transitionDelay = `${delay}ms`;
	const io = new IntersectionObserver(
		(entries) => {
			for (const e of entries) {
				if (e.isIntersecting) {
					node.classList.add('in');
					io.disconnect();
				}
			}
		},
		{ rootMargin: '0px 0px -10% 0px' }
	);
	io.observe(node);
	return { destroy: () => io.disconnect() };
}
