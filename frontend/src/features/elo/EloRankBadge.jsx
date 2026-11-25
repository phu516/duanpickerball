const ranks = {
  BRONZE: 'Đồng',
  SILVER: 'Bạc',
  GOLD: 'Vàng',
  PLATINUM: 'Bạch kim',
  DIAMOND: 'Kim cương',
  MASTER: 'Cao thủ',
  CHALLENGER: 'Thách đấu',
};

export default function EloRankBadge({ rank }) {
  if (!ranks[rank]) return null;
  return <span className={`elo-rank elo-rank-${rank.toLowerCase()}`}>{ranks[rank]}</span>;
}
