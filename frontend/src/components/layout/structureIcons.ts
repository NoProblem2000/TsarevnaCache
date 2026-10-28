import {
  Boxes,
  Database,
  Fingerprint,
  List,
  ListOrdered,
  Megaphone,
  Network,
  Repeat,
  Shapes,
  type LucideIcon,
} from 'lucide-react';
import type { StructureType } from '../../api/types';

/** Icon of each distributed object type, shared by the sidebar and the dashboard. */
export const STRUCTURE_ICONS: Record<StructureType, LucideIcon> = {
  MAP: Database,
  REPLICATED_MAP: Repeat,
  MULTI_MAP: Network,
  QUEUE: ListOrdered,
  SET: Boxes,
  LIST: List,
  TOPIC: Megaphone,
  RELIABLE_TOPIC: Megaphone,
  FLAKE_ID_GENERATOR: Fingerprint,
  OTHER: Shapes,
};
