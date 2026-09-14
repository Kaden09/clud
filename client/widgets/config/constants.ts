import type { LucideIcon } from "lucide-react";
import { ClipboardList, Clock, Folder, Trash2, UserRound } from "lucide-react";
import type { SVGProps } from "react";
import type { ComponentType } from "react";

interface NavigationItem {
  title: string;
  href: string;
  Icon: ComponentType<SVGProps<SVGSVGElement>> | LucideIcon;
}

type NavigationList = Array<NavigationItem>;

export const navigation: NavigationList = [
  {
    title: "My Drive",
    href: "/drive",
    Icon: Folder,
  },
  {
    title: "Recent",
    href: "/recent",
    Icon: Clock,
  },
  {
    title: "Trash",
    href: "/trash",
    Icon: Trash2,
  },
];