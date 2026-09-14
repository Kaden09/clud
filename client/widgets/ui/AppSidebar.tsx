"use client"

import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
} from "@shared/components/ui/sidebar"
import Link from "next/link"

import { navigation } from "../config/constants";
import { usePathname } from "next/navigation";
import { EllipsisVertical, LogOutIcon, UserIcon } from "lucide-react";
import { DropdownMenu, DropdownMenuContent, DropdownMenuGroup, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger } from "@shared/components/ui/dropdown-menu";
import { Button } from "@shared/components/ui/button";
import { Avatar, AvatarFallback, AvatarImage } from "@shared/components/ui/avatar";

export const AppSidebar = () => {
    const pathname = usePathname();
    
  return (
    <Sidebar>
      <SidebarHeader className="border-b border-border">
        <Link href="/" className="text-2xl font-bold">
          Clud
        </Link>
      </SidebarHeader>
      <SidebarContent>
        <SidebarGroup>
          <SidebarGroupLabel className="uppercase">Навигация</SidebarGroupLabel>
          <SidebarGroupContent className="flex flex-col gap-2">
            <SidebarMenu className="flex flex-col gap-1">
              {navigation.map(({ title, href, Icon }) => (
                <SidebarMenuItem key={title}>
                  <SidebarMenuButton
                    isActive={pathname.includes(href)}
                    render={
                      <Link href={href}>
                        <Icon />
                        {title}
                      </Link>
                    }
                    tooltip={title}
                  />
                </SidebarMenuItem>
              ))}
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
      </SidebarContent>
      <SidebarFooter>
        <div className="flex flex-col gap-2 items-center justify-center border border-accent rounded-2xl py-4 px-4">
          <span className="h-2 w-full rounded-full bg-accent"></span>
          <p>Занято 0 ГБ из 10 ГБ</p>
        </div>
        <SidebarMenu>
          <SidebarMenuItem>
            <DropdownMenu>
              <DropdownMenuTrigger
                render={
                  <SidebarMenuButton
                    size="lg"
                    className="data-[state=open]:bg-sidebar-accent data-[state=open]:text-sidebar-accent-foreground"
                  >
                    <Avatar className="h-9 w-9 grayscale">
                      <AvatarImage />
                      <AvatarFallback>
                        A
                      </AvatarFallback>
                    </Avatar>
                    <div className="grid flex-1 gap-1 text-left text-sm leading-tight">
                      <span className="truncate font-medium">
                        username
                      </span>
                      <span className="text-muted-foreground truncate text-xs">
                        email
                      </span>
                    </div>
                    <EllipsisVertical className="ml-auto size-4" />
                  </SidebarMenuButton>
                }
              />
              <DropdownMenuContent
                className="ml-3 w-(--radix-dropdown-menu-trigger-width) min-w-56 rounded-lg"
                side="right"
                align="end"
                sideOffset={4}
              >
                <DropdownMenuGroup>
                  <DropdownMenuLabel className="p-0 font-normal">
                    <div className="flex items-center gap-2 px-1 py-1.5 text-left text-sm">
                      <Avatar className="h-8 w-8 rounded-lg">
                        <AvatarImage />
                        <AvatarFallback className="rounded-lg">
                          A
                        </AvatarFallback>
                      </Avatar>
                      <div className="grid flex-1 text-left text-sm leading-tight">
                        <span className="truncate font-medium">
                          username
                        </span>
                        <span className="text-muted-foreground truncate text-xs">
                          email
                        </span>
                      </div>
                    </div>
                  </DropdownMenuLabel>
                </DropdownMenuGroup>
                <DropdownMenuSeparator />
                <DropdownMenuGroup className="space-y-1">
                  <Link href="/profile" className="flex items-center gap-2">
                    <DropdownMenuItem className="w-full cursor-pointer">
                      <UserIcon />
                      Аккаунт
                    </DropdownMenuItem>
                  </Link>
                  <DropdownMenuItem
                    variant="destructive"
                    className="w-full cursor-pointer"
                  >
                    <LogOutIcon />
                    Выйти
                  </DropdownMenuItem>
                </DropdownMenuGroup>
              </DropdownMenuContent>
            </DropdownMenu>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarFooter>
    </Sidebar>
  )
}
